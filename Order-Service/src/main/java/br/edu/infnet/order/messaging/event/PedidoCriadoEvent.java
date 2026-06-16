package br.edu.infnet.order.messaging.event;

import br.edu.infnet.order.integration.payment.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento de domínio publicado pelo Order-Service quando um pedido é criado.
 * É consumido pelo Payment-Service para iniciar o processamento do pagamento.
 *
 * <p>O {@code eventId} identifica unicamente este evento e é usado pelo consumidor
 * para garantir idempotência (não processar a mesma mensagem duas vezes).
 */
public record PedidoCriadoEvent(
        UUID eventId,
        UUID orderId,
        String customerName,
        BigDecimal amount,
        PaymentMethod paymentMethod
) {
}
