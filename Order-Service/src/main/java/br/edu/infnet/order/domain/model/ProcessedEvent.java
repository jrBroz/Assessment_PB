package br.edu.infnet.order.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro de eventos já processados, usado para garantir IDEMPOTÊNCIA no consumo.
 *
 * <p>Antes de processar um evento recebido da fila, o listener verifica se o
 * {@code eventId} já existe nesta tabela. Se existir, a mensagem é ignorada — assim,
 * mesmo que o RabbitMQ entregue a mesma mensagem mais de uma vez (entrega "at-least-once"),
 * o efeito no domínio acontece apenas uma vez.
 */
@Data
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    public ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = LocalDateTime.now();
    }
}
