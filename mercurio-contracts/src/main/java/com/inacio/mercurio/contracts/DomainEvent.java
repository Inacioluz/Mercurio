package com.inacio.mercurio.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadados presentes em todo evento do sistema.
 *
 * <p>O {@code eventId} e o que torna o consumo idempotente: como o Kafka entrega
 * ao menos uma vez, um consumidor pode receber o mesmo evento duas vezes — e
 * registra o id ja processado para descartar a repeticao.
 */
public interface DomainEvent {

    /** Identificador unico deste evento, usado para deduplicacao no consumo. */
    UUID eventId();

    /** Pagamento a que o evento se refere. Serve como chave de particao. */
    UUID paymentId();

    /** Momento em que o fato ocorreu no servico de origem. */
    Instant occurredAt();
}
