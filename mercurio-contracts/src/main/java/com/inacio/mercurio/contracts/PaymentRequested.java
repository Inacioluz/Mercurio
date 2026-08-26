package com.inacio.mercurio.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Primeiro evento da saga: a API aceitou o pagamento e ele aguarda analise.
 */
public record PaymentRequested(
        UUID eventId,
        UUID paymentId,
        String payerAccount,
        String payeeAccount,
        BigDecimal amount,
        String currency,
        String description,
        Instant occurredAt
) implements DomainEvent {
}
