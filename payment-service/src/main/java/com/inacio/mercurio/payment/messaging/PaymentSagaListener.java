package com.inacio.mercurio.payment.messaging;

import com.inacio.mercurio.contracts.PaymentApproved;
import com.inacio.mercurio.contracts.PaymentFailed;
import com.inacio.mercurio.contracts.PaymentRejected;
import com.inacio.mercurio.contracts.PaymentSettled;
import com.inacio.mercurio.contracts.Topics;
import com.inacio.mercurio.payment.domain.PaymentStatus;
import com.inacio.mercurio.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Acompanha a saga: cada evento dos demais servicos vira uma mudanca de estado
 * do pagamento.
 *
 * <p>Este servico nao decide nada sobre risco ou saldo — ele apenas reflete o
 * que os especialistas decidiram, mantendo a visao consolidada que a API expoe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSagaListener {

    private final PaymentService paymentService;
    private final EventDeduplicator deduplicator;

    @KafkaListener(topics = Topics.PAYMENT_APPROVED, groupId = "payment-service")
    public void onApproved(@Payload PaymentApproved event) {
        if (!deduplicator.claim(event.eventId(), Topics.PAYMENT_APPROVED)) {
            return;
        }
        paymentService.applyTransition(event.paymentId(), PaymentStatus.APPROVED, event.riskScore(), null);
    }

    @KafkaListener(topics = Topics.PAYMENT_REJECTED, groupId = "payment-service")
    public void onRejected(@Payload PaymentRejected event) {
        if (!deduplicator.claim(event.eventId(), Topics.PAYMENT_REJECTED)) {
            return;
        }
        paymentService.applyTransition(event.paymentId(), PaymentStatus.REJECTED,
                event.riskScore(), joinReasons(event.reasons()));
    }

    @KafkaListener(topics = Topics.PAYMENT_SETTLED, groupId = "payment-service")
    public void onSettled(@Payload PaymentSettled event) {
        if (!deduplicator.claim(event.eventId(), Topics.PAYMENT_SETTLED)) {
            return;
        }
        paymentService.applyTransition(event.paymentId(), PaymentStatus.SETTLED, null, null);
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED, groupId = "payment-service")
    public void onFailed(@Payload PaymentFailed event) {
        if (!deduplicator.claim(event.eventId(), Topics.PAYMENT_FAILED)) {
            return;
        }
        paymentService.applyTransition(event.paymentId(), PaymentStatus.FAILED, null, event.reason());
    }

    private String joinReasons(List<String> reasons) {
        return reasons == null || reasons.isEmpty() ? "Barrado pela analise de risco" : String.join("; ", reasons);
    }
}
