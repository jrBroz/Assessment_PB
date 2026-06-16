package br.com.infnet.payment.messaging.event;

import br.com.infnet.payment.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Evento de domínio recebido do Order-Service quando um pedido é criado.
 * Dispara o processamento do pagamento neste serviço.
 *
 * <p>Espelha o record publicado pelo Order-Service. Como usamos
 * {@code TypePrecedence.INFERRED} no conversor, os campos são desserializados pelo nome,
 * mesmo o record estando em pacote diferente do produtor.
 */
public record PedidoCriadoEvent(
        UUID eventId,
        UUID orderId,
        String customerName,
        BigDecimal amount,
        PaymentMethod paymentMethod
) {
}
