package com.inacio.mercurio.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * O valor saiu de uma conta e entrou na outra. Estado terminal de sucesso.
 */
public record PaymentSettled(
        UUID eventId,
        UUID paymentId,
        /** Agrupa as duas partidas do lancamento no razao. */
        UUID ledgerTransactionId,
        String payerAccount,
        String payeeAccount,
        BigDecimal amount,
        BigDecimal payerBalanceAfter,
        Instant occurredAt
) implements DomainEvent {
}
