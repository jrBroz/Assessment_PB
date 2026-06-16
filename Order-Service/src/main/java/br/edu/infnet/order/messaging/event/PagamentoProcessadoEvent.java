package br.edu.infnet.order.messaging.event;

import java.util.UUID;

/**
 * Evento de domínio publicado pelo Payment-Service quando um pagamento é processado.
 * É consumido pelo Order-Service para atualizar o status do pedido.
 *
 * <p>O campo {@code status} carrega o resultado do pagamento ("APPROVED" ou "REJECTED").
 * Usamos String para evitar acoplar os enums dos dois microsserviços.
 */
public record PagamentoProcessadoEvent(
        UUID eventId,
        UUID orderId,
        UUID paymentId,
        String status
) {
}
