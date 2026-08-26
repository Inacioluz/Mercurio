package com.inacio.mercurio.antifraud.service;

import com.inacio.mercurio.antifraud.domain.RiskAssessment;
import com.inacio.mercurio.contracts.PaymentRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Pontuacao de risco por regras somatorias.
 *
 * <p>Cada regra contribui com pontos; a soma decide. A alternativa — a primeira
 * regra que dispara reprova — e mais simples, mas nao distingue um pagamento
 * levemente atipico de um claramente fraudulento, e a pontuacao acumulada e o
 * que permite ajustar o limiar sem reescrever as regras.
 *
 * <p>As regras aqui sao deterministicas de proposito: um portfolio precisa de
 * comportamento reproduzivel, e um modelo estatistico tornaria os testes
 * instaveis sem acrescentar nada a arquitetura.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskEngine {

    /** Acima deste valor o pagamento e barrado. */
    private static final int REJECTION_THRESHOLD = 70;

    private static final BigDecimal HIGH_VALUE = new BigDecimal("50000.00");
    private static final BigDecimal MEDIUM_VALUE = new BigDecimal("10000.00");
    private static final BigDecimal ROUND_VALUE_MIN = new BigDecimal("1000.00");

    /** Janela e limite da regra de velocidade. */
    private static final Duration VELOCITY_WINDOW = Duration.ofMinutes(1);
    private static final int VELOCITY_LIMIT = 5;

    private final StringRedisTemplate redisTemplate;

    @Value("${mercurio.antifraud.blocked-accounts:}")
    private List<String> blockedAccounts = List.of();

    public Result evaluate(PaymentRequested payment) {
        List<RiskAssessment.TriggeredRule> triggered = new ArrayList<>();

        evaluateAmount(payment, triggered);
        evaluateVelocity(payment, triggered);
        evaluateBlocklist(payment, triggered);
        evaluateRoundValue(payment, triggered);

        int score = Math.min(100, triggered.stream()
                .mapToInt(RiskAssessment.TriggeredRule::getPoints)
                .sum());

        return new Result(score, score >= REJECTION_THRESHOLD, triggered);
    }

    private void evaluateAmount(PaymentRequested payment, List<RiskAssessment.TriggeredRule> triggered) {
        if (payment.amount().compareTo(HIGH_VALUE) > 0) {
            triggered.add(rule("HIGH_VALUE",
                    "Valor acima de " + HIGH_VALUE + " " + payment.currency(), 80));
        } else if (payment.amount().compareTo(MEDIUM_VALUE) > 0) {
            triggered.add(rule("MEDIUM_VALUE",
                    "Valor acima de " + MEDIUM_VALUE + " " + payment.currency(), 25));
        }
    }

    /**
     * Conta pagamentos da mesma conta numa janela curta. O contador vive no
     * Redis com TTL, o que da uma janela deslizante barata sem varrer o Mongo a
     * cada avaliacao.
     */
    private void evaluateVelocity(PaymentRequested payment, List<RiskAssessment.TriggeredRule> triggered) {
        String key = "mercurio:velocity:" + payment.payerAccount();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, VELOCITY_WINDOW);
            }
            if (count != null && count > VELOCITY_LIMIT) {
                // Pontua acima do limiar de propósito: uma rajada de pagamentos da
                // mesma conta e o padrao classico de credencial roubada sendo
                // testada, e barra sozinha — sem depender de o valor tambem
                // chamar atencao, que e justamente o que o fraudador evita.
                triggered.add(rule("HIGH_VELOCITY",
                        count + " pagamentos em " + VELOCITY_WINDOW.toSeconds() + "s, limite " + VELOCITY_LIMIT,
                        REJECTION_THRESHOLD));
            }
        } catch (RuntimeException ex) {
            // Sem Redis a regra de velocidade fica cega, mas as demais continuam
            // valendo. Reprovar tudo por indisponibilidade seria pior.
            log.warn("Regra de velocidade indisponivel: {}", ex.getMessage());
        }
    }

    private void evaluateBlocklist(PaymentRequested payment, List<RiskAssessment.TriggeredRule> triggered) {
        if (blockedAccounts.contains(payment.payerAccount()) || blockedAccounts.contains(payment.payeeAccount())) {
            triggered.add(rule("BLOCKED_ACCOUNT", "Conta em lista de bloqueio", 100));
        }
    }

    /** Valores redondos e altos aparecem com frequencia em teste de cartao. */
    private void evaluateRoundValue(PaymentRequested payment, List<RiskAssessment.TriggeredRule> triggered) {
        BigDecimal amount = payment.amount();
        boolean round = amount.stripTrailingZeros().scale() <= 0
                && amount.remainder(ROUND_VALUE_MIN).compareTo(BigDecimal.ZERO) == 0;
        if (round && amount.compareTo(ROUND_VALUE_MIN) >= 0) {
            triggered.add(rule("ROUND_AMOUNT", "Valor redondo de " + amount, 10));
        }
    }

    private RiskAssessment.TriggeredRule rule(String code, String description, int points) {
        return RiskAssessment.TriggeredRule.builder()
                .code(code).description(description).points(points).build();
    }

    public record Result(int score, boolean rejected, List<RiskAssessment.TriggeredRule> triggeredRules) {

        public List<String> reasons() {
            return triggeredRules.stream().map(RiskAssessment.TriggeredRule::getDescription).toList();
        }
    }
}
