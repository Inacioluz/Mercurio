package com.inacio.mercurio.antifraud.repository;

import com.inacio.mercurio.antifraud.domain.RiskAssessment;
import com.inacio.mercurio.antifraud.domain.RiskDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskAssessmentRepository extends MongoRepository<RiskAssessment, String> {

    Optional<RiskAssessment> findByPaymentId(UUID paymentId);

    Page<RiskAssessment> findByDecisionOrderByAssessedAtDesc(RiskDecision decision, Pageable pageable);

    Page<RiskAssessment> findByPayerAccountOrderByAssessedAtDesc(String payerAccount, Pageable pageable);

    /** Volume recente da conta — entrada da regra de velocidade. */
    long countByPayerAccountAndAssessedAtAfter(String payerAccount, Instant since);

    long countByDecision(RiskDecision decision);
}
