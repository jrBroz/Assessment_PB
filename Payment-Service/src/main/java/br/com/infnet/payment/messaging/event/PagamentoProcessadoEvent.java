package br.com.infnet.payment.messaging.event;

import java.util.UUID;

/**
 * Evento de domínio publicado por este serviço após processar um pagamento.
 * É consumido pelo Order-Service para atualizar o status do pedido.
 *
 * <p>O campo {@code status} carrega "APPROVED" ou "REJECTED".
 */
public record PagamentoProcessadoEvent(
        UUID eventId,
        UUID orderId,
        UUID paymentId,
        String status
) {
}
