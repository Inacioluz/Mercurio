package com.inacio.mercurio.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Notificacao registrada para uma das partes do pagamento.
 *
 * <p>O indice composto {@code (eventId, recipientAccount)} e o que torna o
 * consumo idempotente: uma reentrega do mesmo evento tenta gravar as mesmas
 * duas notificacoes e esbarra na unicidade, em vez de avisar o cliente duas
 * vezes.
 */
@Document(collection = "notifications")
@CompoundIndex(name = "uk_event_recipient", def = "{'eventId': 1, 'recipientAccount': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private String id;

    /** Evento que originou a notificacao. */
    private UUID eventId;

    @Indexed
    private UUID paymentId;

    @Indexed
    private String recipientAccount;

    private NotificationChannel channel;

    /** Tipo do aviso: PAYMENT_SETTLED, PAYMENT_REJECTED, PAYMENT_FAILED. */
    private String type;

    private String subject;
    private String body;

    /**
     * Dados livres do evento de origem. O formato varia por tipo — outro caso
     * em que o documento evita colunas nulas num modelo relacional.
     */
    private Map<String, Object> metadata;

    @Indexed
    private Instant createdAt;
}
