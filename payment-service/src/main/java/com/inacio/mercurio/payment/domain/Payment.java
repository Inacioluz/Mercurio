package com.inacio.mercurio.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Visao do pagamento sob a otica da API. O saldo real vive no ledger-service —
 * aqui fica so o acompanhamento da saga.
 */
@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Chave enviada pelo cliente; garante que um retry nao gere dois pagamentos. */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "payer_account", nullable = false, length = 30)
    private String payerAccount;

    @Column(name = "payee_account", nullable = false, length = 30)
    private String payeeAccount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    /** Preenchido quando a saga termina em REJECTED ou FAILED. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Avanca o pagamento se a transicao for valida. Devolve false quando o
     * estado atual ja e terminal ou o destino nao e alcancavel — o consumidor
     * usa isso para descartar um evento fora de ordem sem tratar como erro.
     */
    public boolean transitionTo(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            return false;
        }
        this.status = target;
        return true;
    }
}
