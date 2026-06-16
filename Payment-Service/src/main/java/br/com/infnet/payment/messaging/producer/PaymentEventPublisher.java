package br.com.infnet.payment.messaging.producer;

import br.com.infnet.payment.config.RabbitMQConfig;
import br.com.infnet.payment.messaging.event.PagamentoProcessadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Produtor de eventos do Payment-Service.
 * Publica o evento de domínio {@code PagamentoProcessado} para que o Order-Service
 * atualize o status do pedido de forma assíncrona.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPagamentoProcessado(PagamentoProcessadoEvent event) {
        log.info("Publicando PagamentoProcessado: eventId={}, orderId={}, status={}",
                event.eventId(), event.orderId(), event.status());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_PAGAMENTO_PROCESSADO,
                event
        );
    }
}
