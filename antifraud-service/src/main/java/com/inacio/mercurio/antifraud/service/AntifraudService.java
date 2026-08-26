package com.inacio.mercurio.antifraud.service;

import com.inacio.mercurio.antifraud.domain.RiskAssessment;
import com.inacio.mercurio.antifraud.domain.RiskDecision;
import com.inacio.mercurio.antifraud.repository.RiskAssessmentRepository;
import com.inacio.mercurio.contracts.PaymentApproved;
import com.inacio.mercurio.contracts.PaymentRejected;
import com.inacio.mercurio.contracts.PaymentRequested;
import com.inacio.mercurio.contracts.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AntifraudService {

    private final RiskEngine riskEngine;
    private final RiskAssessmentRepository assessmentRepository;

    /**
     * Analisa o pagamento e devolve o evento a publicar.
     *
     * <p>Idempotente pelo indice unico em {@code paymentId}: uma reentrega
     * reaproveita o laudo original em vez de reavaliar. Reavaliar seria pior do
     * que redundante — a regra de velocidade conta tentativas, e reprocessar o
     * mesmo evento inflaria o contador e poderia reprovar um pagamento que ja
     * havia sido aprovado.
     */
    public Outcome assess(PaymentRequested payment) {
        Optional<RiskAssessment> existing = assessmentRepository.findByPaymentId(payment.paymentId());
        if (existing.isPresent()) {
            log.debug("Pagamento {} ja avaliado, reaproveitando o laudo", payment.paymentId());
            return toOutcome(payment, existing.get(), true);
        }

        long startedAt = System.nanoTime();
        RiskEngine.Result result = riskEngine.evaluate(payment);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        RiskAssessment assessment = RiskAssessment.builder()
                .paymentId(payment.paymentId())
                .payerAccount(payment.payerAccount())
                .payeeAccount(payment.payeeAccount())
                .amount(payment.amount())
                .currency(payment.currency())
                .riskScore(result.score())
                .decision(result.rejected() ? RiskDecision.REJECTED : RiskDecision.APPROVED)
                .triggeredRules(result.triggeredRules())
                .assessedAt(Instant.now())
                .evaluationMillis(elapsedMillis)
                .build();

        try {
            assessment = assessmentRepository.save(assessment);
        } catch (DuplicateKeyException ex) {
            // Duas entregas do mesmo evento em paralelo: quem perdeu reaproveita
            // o laudo de quem gravou primeiro.
            return assessmentRepository.findByPaymentId(payment.paymentId())
                    .map(saved -> toOutcome(payment, saved, true))
                    .orElseThrow(() -> ex);
        }

        log.info("Pagamento {} avaliado: score={} decisao={} regras={}",
                payment.paymentId(), assessment.getRiskScore(), assessment.getDecision(),
                result.triggeredRules().stream().map(RiskAssessment.TriggeredRule::getCode).toList());

        return toOutcome(payment, assessment, false);
    }

    private Outcome toOutcome(PaymentRequested payment, RiskAssessment assessment, boolean replayed) {
        if (assessment.getDecision() == RiskDecision.REJECTED) {
            return new Outcome(new PaymentRejected(
                    UUID.randomUUID(),
                    payment.paymentId(),
                    assessment.getRiskScore(),
                    assessment.getTriggeredRules().stream()
                            .map(RiskAssessment.TriggeredRule::getDescription)
                            .toList(),
                    Instant.now()), Topics.PAYMENT_REJECTED, replayed);
        }

        return new Outcome(new PaymentApproved(
                UUID.randomUUID(),
                payment.paymentId(),
                payment.payerAccount(),
                payment.payeeAccount(),
                payment.amount(),
                payment.currency(),
                assessment.getRiskScore(),
                Instant.now()), Topics.PAYMENT_APPROVED, replayed);
    }

    /** Evento resultante, o topico de destino e se veio de um laudo ja existente. */
    public record Outcome(Object event, String topic, boolean replayed) {
    }
}
