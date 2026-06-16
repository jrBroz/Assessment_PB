package br.com.infnet.payment.domain.model;

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
 * <p>Antes de processar o evento {@code PedidoCriado}, o listener verifica se o
 * {@code eventId} já existe nesta tabela. Se existir, ignora — evitando criar pagamentos
 * duplicados caso o RabbitMQ entregue a mesma mensagem mais de uma vez.
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
