package com.inacio.mercurio.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Maquina de estados do pagamento")
class PaymentStatusTest {

    @Test
    @DisplayName("o caminho feliz vai de PENDING a SETTLED passando por APPROVED")
    void happyPath() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.APPROVED)).isTrue();
        assertThat(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.SETTLED)).isTrue();
    }

    @Test
    @DisplayName("nao se pula a analise: PENDING nao vai direto a SETTLED")
    void cannotSkipApproval() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.SETTLED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"REJECTED", "SETTLED", "FAILED"})
    @DisplayName("estado terminal nao aceita nenhuma transicao")
    void terminalStatesAreFinal(PaymentStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(terminal.allowedTransitions()).isEmpty();

        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(terminal.canTransitionTo(target))
                    .as("%s -> %s deveria ser recusada", terminal, target)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("um evento fora de ordem nao retrocede o pagamento")
    void outOfOrderEventDoesNotRegress() {
        Payment payment = Payment.builder()
                .status(PaymentStatus.SETTLED)
                .amount(new BigDecimal("100.00"))
                .build();

        // Reentrega tardia do PaymentApproved depois de o pagamento ja ter liquidado.
        boolean applied = payment.transitionTo(PaymentStatus.APPROVED);

        assertThat(applied).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SETTLED);
    }

    @Test
    @DisplayName("um pagamento aprovado pode falhar na liquidacao")
    void approvedCanStillFail() {
        Payment payment = Payment.builder()
                .status(PaymentStatus.APPROVED)
                .amount(new BigDecimal("100.00"))
                .build();

        assertThat(payment.transitionTo(PaymentStatus.FAILED)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("um pagamento aprovado nao volta a ser reprovado pelo antifraude")
    void approvedCannotBeRejected() {
        assertThat(PaymentStatus.APPROVED.canTransitionTo(PaymentStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("PENDING e o unico estado nao terminal alem de APPROVED")
    void nonTerminalStates() {
        assertThat(PaymentStatus.PENDING.isTerminal()).isFalse();
        assertThat(PaymentStatus.APPROVED.isTerminal()).isFalse();
    }
}
