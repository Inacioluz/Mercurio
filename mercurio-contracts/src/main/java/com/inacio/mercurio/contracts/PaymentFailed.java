package com.inacio.mercurio.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * A liquidacao nao foi possivel — saldo insuficiente, conta inexistente ou
 * inativa. Estado terminal de falha.
 */
public record PaymentFailed(
        UUID eventId,
        UUID paymentId,
        /** Codigo estavel: INSUFFICIENT_FUNDS, ACCOUNT_NOT_FOUND, ACCOUNT_INACTIVE. */
        String reasonCode,
        String reason,
        Instant occurredAt
) implements DomainEvent {
}
