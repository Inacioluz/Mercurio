package com.inacio.mercurio.contracts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A analise antifraude liberou o pagamento. O ledger e quem reage a este evento.
 */
public record PaymentApproved(
        UUID eventId,
        UUID paymentId,
        String payerAccount,
        String payeeAccount,
        BigDecimal amount,
        String currency,
        /** Pontuacao de risco de 0 (seguro) a 100 (critico). */
        int riskScore,
        Instant occurredAt
) implements DomainEvent {
}
