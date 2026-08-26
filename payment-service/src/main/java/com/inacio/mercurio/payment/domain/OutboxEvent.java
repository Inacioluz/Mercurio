package com.inacio.mercurio.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento a publicar, gravado na <b>mesma transacao</b> que altera o pagamento.
 *
 * <p>Publicar direto no Kafka dentro do metodo de negocio abre uma janela em que
 * os dois recursos discordam: se a transacao do banco falha depois do envio, o
 * mundo recebeu um evento sobre um fato que nao aconteceu; se o envio falha
 * depois do commit, o fato aconteceu e ninguem soube. Gravando o evento na
 * propria transacao, ele existe exatamente quando o fato existe — e um processo
 * separado ({@code OutboxRelay}) se encarrega de entregar.
 *
 * <p>Isso troca "exatamente uma vez" (impossivel entre dois sistemas) por "ao
 * menos uma vez" com deduplicacao no consumidor, via {@code eventId}.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    /** Chave de particao — o paymentId, para preservar ordem por pagamento. */
    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Nulo enquanto pendente; preenchido quando o broker confirma o envio. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }
}
