package com.inacio.mercurio.contracts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A analise antifraude barrou o pagamento. Estado terminal: o razao nao e tocado.
 */
public record PaymentRejected(
        UUID eventId,
        UUID paymentId,
        int riskScore,
        /** Regras de risco que dispararam, em linguagem legivel. */
        List<String> reasons,
        Instant occurredAt
) implements DomainEvent {
}
