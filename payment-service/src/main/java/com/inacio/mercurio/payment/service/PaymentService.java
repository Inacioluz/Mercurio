package com.inacio.mercurio.payment.service;

import com.inacio.mercurio.contracts.PaymentRequested;
import com.inacio.mercurio.contracts.Topics;
import com.inacio.mercurio.payment.api.dto.CreatePaymentRequest;
import com.inacio.mercurio.payment.api.dto.PaymentResponse;
import com.inacio.mercurio.payment.domain.Payment;
import com.inacio.mercurio.payment.domain.PaymentStatus;
import com.inacio.mercurio.payment.exception.BusinessRuleException;
import com.inacio.mercurio.payment.exception.IdempotencyConflictException;
import com.inacio.mercurio.payment.exception.PaymentNotFoundException;
import com.inacio.mercurio.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxWriter outboxWriter;
    private final IdempotencyService idempotencyService;

    /**
     * Aceita o pagamento e enfileira o evento de inicio da saga.
     *
     * <p>A resposta e 202, nao 201: quando ela volta, o pagamento existe mas
     * ainda nao foi analisado nem liquidado. O cliente acompanha pelo GET ou
     * espera a notificacao.
     */
    @Transactional
    public CreationResult create(String idempotencyKey, CreatePaymentRequest request) {
        if (request.payerAccount().equals(request.payeeAccount())) {
            throw BusinessRuleException.sameAccount();
        }

        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        String currency = request.currencyOrDefault();
        String fingerprint = idempotencyService.fingerprint(
                request.payerAccount(), request.payeeAccount(), amount.toPlainString(), currency);

        Optional<CreationResult> replay = replayIfDuplicate(idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

        Payment payment = Payment.builder()
                .idempotencyKey(idempotencyKey)
                .payerAccount(request.payerAccount())
                .payeeAccount(request.payeeAccount())
                .amount(amount)
                .currency(currency)
                .description(request.description())
                .status(PaymentStatus.PENDING)
                .build();

        try {
            payment = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException ex) {
            // Duas requisicoes com a mesma chave chegaram juntas e ambas passaram
            // pela verificacao acima. O indice unico decide o empate; quem perdeu
            // devolve o pagamento de quem ganhou.
            log.debug("Corrida na chave de idempotencia {}, devolvendo o pagamento existente", idempotencyKey);
            return replayIfDuplicate(idempotencyKey, fingerprint)
                    .orElseThrow(() -> new IdempotencyConflictException(idempotencyKey));
        }

        // Mesma transacao do INSERT acima: o evento so existe se o pagamento existir.
        outboxWriter.enqueue(Topics.PAYMENT_REQUESTED, new PaymentRequested(
                UUID.randomUUID(),
                payment.getId(),
                payment.getPayerAccount(),
                payment.getPayeeAccount(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getDescription(),
                Instant.now()));

        idempotencyService.remember(idempotencyKey, payment.getId(), fingerprint);
        log.info("Pagamento {} aceito: {} {} de {} para {}", payment.getId(), payment.getCurrency(),
                payment.getAmount(), payment.getPayerAccount(), payment.getPayeeAccount());

        return new CreationResult(PaymentResponse.from(payment), false);
    }

    /**
     * Devolve o pagamento original quando a chave ja foi usada com o mesmo
     * corpo; recusa quando o corpo difere.
     */
    private Optional<CreationResult> replayIfDuplicate(String idempotencyKey, String fingerprint) {
        Optional<IdempotencyService.CachedResult> cached = idempotencyService.lookup(idempotencyKey);
        if (cached.isPresent() && !cached.get().fingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(idempotencyKey);
        }

        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    String storedFingerprint = idempotencyService.fingerprint(
                            existing.getPayerAccount(), existing.getPayeeAccount(),
                            existing.getAmount().toPlainString(), existing.getCurrency());
                    if (!storedFingerprint.equals(fingerprint)) {
                        throw new IdempotencyConflictException(idempotencyKey);
                    }
                    return new CreationResult(PaymentResponse.from(existing), true);
                });
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(PaymentStatus status, String account, Pageable pageable) {
        Page<Payment> payments;
        if (status != null) {
            payments = paymentRepository.findByStatus(status, pageable);
        } else if (account != null && !account.isBlank()) {
            payments = paymentRepository.findByPayerAccountOrPayeeAccount(account, account, pageable);
        } else {
            payments = paymentRepository.findAll(pageable);
        }
        return payments.map(PaymentResponse::from);
    }

    /**
     * Aplica uma mudanca de estado vinda de um evento. Roda em transacao propria
     * ({@code REQUIRES_NEW}) e sob lock da linha, porque dois eventos do mesmo
     * pagamento podem chegar por topicos diferentes ao mesmo tempo.
     *
     * @return false quando a transicao e invalida — evento fora de ordem, a
     *         descartar sem erro.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean applyTransition(UUID paymentId, PaymentStatus target, Integer riskScore, String failureReason) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (!payment.transitionTo(target)) {
            log.warn("Transicao ignorada para o pagamento {}: {} -> {}",
                    paymentId, payment.getStatus(), target);
            return false;
        }

        if (riskScore != null) {
            payment.setRiskScore(riskScore);
        }
        if (failureReason != null) {
            payment.setFailureReason(failureReason);
        }

        paymentRepository.save(payment);
        log.info("Pagamento {} -> {}", paymentId, target);
        return true;
    }

    /** Resultado da criacao; {@code replayed} indica resposta de uma chave repetida. */
    public record CreationResult(PaymentResponse payment, boolean replayed) {
    }
}
