package br.edu.infnet.order.config;

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
 * Topologia de mensageria do Order-Service.
 *
 * <p>Fluxo assíncrono de pagamento:
 * <ul>
 *   <li>O Order PUBLICA o evento {@code PedidoCriado} (routing key {@value #ROUTING_PEDIDO_CRIADO}).</li>
 *   <li>O Order CONSOME o evento {@code PagamentoProcessado} na fila {@value #QUEUE_PAGAMENTO_PROCESSADO}.</li>
 * </ul>
 *
 * <p>Todas as filas e o exchange são declarados como duráveis (sobrevivem a um restart do broker)
 * e cada fila possui uma Dead Letter Queue (DLQ) para onde vão as mensagens que falham repetidamente.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "ecommerce.exchange";
    public static final String DLX = "ecommerce.dlx";

    // Eventos de domínio (routing keys)
    public static final String ROUTING_PEDIDO_CRIADO = "pedido.criado";
    public static final String ROUTING_PAGAMENTO_PROCESSADO = "pagamento.processado";

    // Fila que ESTE serviço consome
    public static final String QUEUE_PAGAMENTO_PROCESSADO = "order.pagamento-processado.queue";
    public static final String QUEUE_PAGAMENTO_PROCESSADO_DLQ = "order.pagamento-processado.dlq";

    @Bean
    public TopicExchange ecommerceExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    public Queue pagamentoProcessadoQueue() {
        return QueueBuilder.durable(QUEUE_PAGAMENTO_PROCESSADO)
                // Se a mensagem falhar todas as tentativas, é roteada para a DLX/DLQ.
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_PAGAMENTO_PROCESSADO_DLQ)
                .build();
    }

    @Bean
    public Queue pagamentoProcessadoDlq() {
        return QueueBuilder.durable(QUEUE_PAGAMENTO_PROCESSADO_DLQ).build();
    }

    @Bean
    public Binding pagamentoProcessadoBinding() {
        return BindingBuilder.bind(pagamentoProcessadoQueue())
                .to(ecommerceExchange())
                .with(ROUTING_PAGAMENTO_PROCESSADO);
    }

    @Bean
    public Binding pagamentoProcessadoDlqBinding() {
        return BindingBuilder.bind(pagamentoProcessadoDlq())
                .to(deadLetterExchange())
                .with(QUEUE_PAGAMENTO_PROCESSADO_DLQ);
    }

    /**
     * Conversor JSON. Como os eventos vivem em pacotes diferentes em cada microsserviço,
     * usamos {@code TypePrecedence.INFERRED}: o tipo é deduzido pela assinatura do método
     * @RabbitListener, ignorando o header {@code __TypeId__} enviado pelo produtor.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter("br.edu.infnet.order.*");
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
