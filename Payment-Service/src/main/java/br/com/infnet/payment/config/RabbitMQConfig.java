package br.com.infnet.payment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de mensageria do Payment-Service.
 *
 * <p>Fluxo assíncrono de pagamento:
 * <ul>
 *   <li>O Payment CONSOME o evento {@code PedidoCriado} na fila {@value #QUEUE_PEDIDO_CRIADO}.</li>
 *   <li>O Payment PUBLICA o evento {@code PagamentoProcessado} (routing key {@value #ROUTING_PAGAMENTO_PROCESSADO}).</li>
 * </ul>
 *
 * <p>O exchange e as filas precisam ser idênticos aos declarados no Order-Service.
 * Como ambos declaram os mesmos recursos (operação idempotente no RabbitMQ), não importa
 * qual serviço sobe primeiro.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "ecommerce.exchange";
    public static final String DLX = "ecommerce.dlx";

    // Eventos de domínio (routing keys)
    public static final String ROUTING_PEDIDO_CRIADO = "pedido.criado";
    public static final String ROUTING_PAGAMENTO_PROCESSADO = "pagamento.processado";

    // Fila que ESTE serviço consome
    public static final String QUEUE_PEDIDO_CRIADO = "payment.pedido-criado.queue";
    public static final String QUEUE_PEDIDO_CRIADO_DLQ = "payment.pedido-criado.dlq";

    @Bean
    public TopicExchange ecommerceExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    public Queue pedidoCriadoQueue() {
        return QueueBuilder.durable(QUEUE_PEDIDO_CRIADO)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_PEDIDO_CRIADO_DLQ)
                .build();
    }

    @Bean
    public Queue pedidoCriadoDlq() {
        return QueueBuilder.durable(QUEUE_PEDIDO_CRIADO_DLQ).build();
    }

    @Bean
    public Binding pedidoCriadoBinding() {
        return BindingBuilder.bind(pedidoCriadoQueue())
                .to(ecommerceExchange())
                .with(ROUTING_PEDIDO_CRIADO);
    }

    @Bean
    public Binding pedidoCriadoDlqBinding() {
        return BindingBuilder.bind(pedidoCriadoDlq())
                .to(deadLetterExchange())
                .with(QUEUE_PEDIDO_CRIADO_DLQ);
    }

    /**
     * Conversor JSON com {@code TypePrecedence.INFERRED}: o tipo do payload é deduzido
     * pela assinatura do @RabbitListener, permitindo que o evento exista em pacotes
     * diferentes em cada microsserviço.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter("br.com.infnet.payment.*");
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
