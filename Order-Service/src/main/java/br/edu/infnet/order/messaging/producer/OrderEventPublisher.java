package br.edu.infnet.order.messaging.producer;

import br.edu.infnet.order.config.RabbitMQConfig;
import br.edu.infnet.order.messaging.event.PedidoCriadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Produtor de eventos do Order-Service.
 * Publica o evento de domínio {@code PedidoCriado} no exchange para que o
 * Payment-Service o consuma de forma assíncrona.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPedidoCriado(PedidoCriadoEvent event) {
        log.info("Publicando PedidoCriado: eventId={}, orderId={}", event.eventId(), event.orderId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_PEDIDO_CRIADO,
                event
        );
    }
}
