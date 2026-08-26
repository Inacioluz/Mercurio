package com.inacio.mercurio.antifraud.service;

import com.inacio.mercurio.contracts.PaymentRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RiskEngine")
class RiskEngineTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RiskEngine riskEngine;

    @BeforeEach
    void setUp() {
        riskEngine = new RiskEngine(redisTemplate);
        ReflectionTestUtils.setField(riskEngine, "blockedAccounts", List.of("ACC-6666"));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Por padrao, primeira operacao da conta na janela.
        when(valueOperations.increment(anyString())).thenReturn(1L);
    }

    @Nested
    @DisplayName("valor")
    class Amount {

        @Test
        @DisplayName("aprova um pagamento comum com score zero")
        void approvesOrdinaryPayment() {
            RiskEngine.Result result = riskEngine.evaluate(payment("ACC-1001", "ACC-2002", "150.75"));

            assertThat(result.score()).isZero();
            assertThat(result.rejected()).isFalse();
            assertThat(result.triggeredRules()).isEmpty();
        }

        @Test
        @DisplayName("barra valor acima de 50.000")
        void rejectsHighValue() {
            RiskEngine.Result result = riskEngine.evaluate(payment("ACC-1001", "ACC-2002", "50000.01"));

            assertThat(result.rejected()).isTrue();
            assertThat(result.triggeredRules()).extracting("code").contains("HIGH_VALUE");
        }

        @Test
        @DisplayName("pontua sem barrar valor entre 10.000 e 50.000")
        void flagsMediumValueWithoutRejecting() {
            RiskEngine.Result result = riskEngine.evaluate(payment("ACC-1001", "ACC-2002", "10000.01"));

            assertThat(result.rejected()).isFalse();
            assertThat(result.score()).isEqualTo(25);
            assertThat(result.triggeredRules()).extracting("code").containsExactly("MEDIUM_VALUE");
        }

        @Test
        @DisplayName("o limiar de 50.000 nao dispara em cima do valor exato")
        void thresholdIsExclusive() {
            RiskEngine.Result result = riskEngine.evaluate(payment("ACC-1001", "ACC-2002", "50000.00"));

            assertThat(result.triggeredRules()).extracting("code").doesNotContain("HIGH_VALUE");
        }
    }

    @Nested
    @DisplayName("velocidade")
    class Velocity {

        @Test
        @DisplayName("barra a conta que estoura o limite da janela")
        void rejectsBurst() {
            when(valueOperations.increment(anyString())).thenReturn(6L);

            RiskEngine.Result result = riskEngine.evaluate(payment("ACC-1001", "ACC-2002", "10.00"));

            assertThat(result.triggeredRules()).extracting("code").contains("HIGH_VELOCITY");
            assertThat(result.rejected()).isTrue();
        }

        @Test
        @DisplayName("nao dispara no limite exato")
        void allowsUpToLimit() {
            when(valueOperations.increment(anyString())).thenReturn(5L);

            RiskEngine.Result result = riskEngine.evaluate(payment("ACC-1001", "ACC-2002", "10.00"));

            assertThat(result.triggeredRules()).extracting("code").doesNotContain("HIGH_VELOCITY");
        }

        @Test
        @DisplayName("com o Redis fora do ar, segue avaliando as demais regras")
        void survivesRedisOutage() {
            when(valueOperations.increment(anyString()))
                    .thenThrow(new org.springframework.dao.QueryTimeoutException("redis indisponivel"));

            RiskEngine.Result result = riskEngine.evaluate(payment("ACC-1001", "ACC-2002", "60000.00"));

            // A regra de valor continua valendo mesmo sem a de velocidade.
            assertThat(result.rejected()).isTrue();
            assertThat(result.triggeredRules()).extracting("code").contains("HIGH_VALUE");
        }
    }

    @Nested
    @DisplayName("lista de bloqueio")
    class Blocklist {

        @Test
        @DisplayName("barra quando a origem esta bloqueada")
        void rejectsBlockedPayer() {
            RiskEngine.Result result = riskEngine.evaluate(payment("ACC-6666", "ACC-2002", "10.00"));

            assertThat(result.rejected()).isTrue();
            assertThat(result.score()).isEqualTo(100);
        }

        @Test
        @DisplayName("barra quando o destino esta bloqueado")
        void rejectsBlockedPayee() {
            RiskEngine.Result result = riskEngine.evaluate(payment("ACC-1001", "ACC-6666", "10.00"));

            assertThat(result.rejected()).isTrue();
        }
    }

    @Test
    @DisplayName("a pontuacao soma as regras e nao passa de 100")
    void scoreIsCappedAtHundred() {
        when(valueOperations.increment(anyString())).thenReturn(50L);

        // Bloqueio (100) + valor alto (80) + velocidade (60) + redondo (10) = 250
        RiskEngine.Result result = riskEngine.evaluate(payment("ACC-6666", "ACC-2002", "75000"));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.triggeredRules()).hasSizeGreaterThan(2);
    }

    @Test
    @DisplayName("os motivos saem legiveis para o cliente")
    void reasonsAreHumanReadable() {
        RiskEngine.Result result = riskEngine.evaluate(payment("ACC-1001", "ACC-2002", "75000.00"));

        assertThat(result.reasons()).isNotEmpty();
        assertThat(result.reasons().getFirst()).contains("Valor acima de");
    }

    private static PaymentRequested payment(String payer, String payee, String amount) {
        return new PaymentRequested(
                UUID.randomUUID(), UUID.randomUUID(), payer, payee,
                new BigDecimal(amount), "BRL", "teste", Instant.now());
    }
}
