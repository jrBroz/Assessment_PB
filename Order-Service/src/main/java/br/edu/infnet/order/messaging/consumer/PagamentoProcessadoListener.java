package br.edu.infnet.order.messaging.consumer;

import br.edu.infnet.order.config.RabbitMQConfig;
import br.edu.infnet.order.domain.enums.OrderStatus;
import br.edu.infnet.order.domain.model.Order;
import br.edu.infnet.order.domain.model.ProcessedEvent;
import br.edu.infnet.order.messaging.event.PagamentoProcessadoEvent;
import br.edu.infnet.order.repository.OrderRepository;
import br.edu.infnet.order.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor do Order-Service.
 * Recebe o evento {@code PagamentoProcessado} publicado pelo Payment-Service e
 * atualiza o status do pedido (CONFIRMED se aprovado, PAYMENT_FAILED se recusado).
 */
@Component
public class PagamentoProcessadoListener {

    private static final Logger log = LoggerFactory.getLogger(PagamentoProcessadoListener.class);

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;

    public PagamentoProcessadoListener(OrderRepository orderRepository,
                                       ProcessedEventRepository processedEventRepository) {
        this.orderRepository = orderRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.QUEUE_PAGAMENTO_PROCESSADO)
    public void onPagamentoProcessado(PagamentoProcessadoEvent event) {
        log.info("Recebido PagamentoProcessado: eventId={}, orderId={}, status={}",
                event.eventId(), event.orderId(), event.status());

        // IDEMPOTÊNCIA: se este evento já foi processado, ignora (entrega duplicada).
        if (processedEventRepository.existsById(event.eventId())) {
            log.warn("Evento {} já processado anteriormente. Ignorando duplicata.", event.eventId());
            return;
        }

        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            // Pedido inexistente: registra como processado para não ficar em retry/DLQ eterno.
            log.error("Pedido {} não encontrado para o pagamento {}.", event.orderId(), event.paymentId());
            processedEventRepository.save(new ProcessedEvent(event.eventId()));
            return;
        }

        if ("APPROVED".equalsIgnoreCase(event.status())) {
            order.setOrderStatus(OrderStatus.CONFIRMED);
        } else {
            order.setOrderStatus(OrderStatus.PAYMENT_FAILED);
        }
        orderRepository.save(order);

        // Marca o evento como processado na MESMA transação da atualização do pedido.
        processedEventRepository.save(new ProcessedEvent(event.eventId()));
        log.info("Pedido {} atualizado para {}.", order.getId(), order.getOrderStatus());
    }
}
