package br.com.infnet.payment.messaging.consumer;

import br.com.infnet.payment.config.RabbitMQConfig;
import br.com.infnet.payment.domain.model.Payment;
import br.com.infnet.payment.domain.model.ProcessedEvent;
import br.com.infnet.payment.messaging.event.PagamentoProcessadoEvent;
import br.com.infnet.payment.messaging.event.PedidoCriadoEvent;
import br.com.infnet.payment.messaging.producer.PaymentEventPublisher;
import br.com.infnet.payment.repository.ProcessedEventRepository;
import br.com.infnet.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumidor do Payment-Service.
 * Recebe o evento {@code PedidoCriado} do Order-Service, processa o pagamento e
 * publica o evento {@code PagamentoProcessado} com o resultado.
 */
@Component
public class PedidoCriadoListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoListener.class);

    private final PaymentService paymentService;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    public PedidoCriadoListener(PaymentService paymentService,
                                ProcessedEventRepository processedEventRepository,
                                PaymentEventPublisher paymentEventPublisher) {
        this.paymentService = paymentService;
        this.processedEventRepository = processedEventRepository;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.QUEUE_PEDIDO_CRIADO)
    public void onPedidoCriado(PedidoCriadoEvent event) {
        log.info("Recebido PedidoCriado: eventId={}, orderId={}, amount={}",
                event.eventId(), event.orderId(), event.amount());

        // IDEMPOTÊNCIA: se o evento já foi processado, não cria pagamento de novo.
        if (processedEventRepository.existsById(event.eventId())) {
            log.warn("Evento {} já processado anteriormente. Ignorando duplicata.", event.eventId());
            return;
        }

        // Processa o pagamento (aprova ou recusa).
        Payment payment = paymentService.processPayment(
                event.orderId(),
                event.amount(),
                event.paymentMethod()
        );

        // Marca como processado na mesma transação do pagamento (idempotência consistente).
        processedEventRepository.save(new ProcessedEvent(event.eventId()));

        // Publica o resultado para o Order-Service.
        paymentEventPublisher.publishPagamentoProcessado(new PagamentoProcessadoEvent(
                UUID.randomUUID(),
                payment.getOrderId(),
                payment.getId(),
                payment.getStatus().name()
        ));

        log.info("Pagamento {} para o pedido {} processado com status {}.",
                payment.getId(), payment.getOrderId(), payment.getStatus());
    }
}
