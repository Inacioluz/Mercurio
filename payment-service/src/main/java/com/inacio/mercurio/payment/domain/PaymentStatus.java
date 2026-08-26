package com.inacio.mercurio.payment.domain;

import java.util.Set;

/**
 * Estados de um pagamento na saga. As transicoes validas estao declaradas aqui
 * para que um evento fora de ordem — o Kafka garante ordem por particao, mas
 * uma reentrega pode chegar depois de um estado terminal — nao consiga
 * retroceder o pagamento.
 */
public enum PaymentStatus {

    /** Aceito pela API, aguardando analise antifraude. */
    PENDING,
    /** Liberado pelo antifraude, aguardando liquidacao no razao. */
    APPROVED,
    /** Barrado pelo antifraude. Terminal. */
    REJECTED,
    /** Valor movimentado no razao. Terminal. */
    SETTLED,
    /** Liquidacao impossivel. Terminal. */
    FAILED;

    public boolean isTerminal() {
        return this == REJECTED || this == SETTLED || this == FAILED;
    }

    /** Estados alcancaveis a partir deste. */
    public Set<PaymentStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> Set.of(APPROVED, REJECTED, FAILED);
            case APPROVED -> Set.of(SETTLED, FAILED);
            case REJECTED, SETTLED, FAILED -> Set.of();
        };
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return allowedTransitions().contains(target);
    }
}
