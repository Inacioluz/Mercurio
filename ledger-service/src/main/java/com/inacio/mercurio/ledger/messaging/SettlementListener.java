package com.inacio.mercurio.ledger.messaging;

import com.inacio.mercurio.contracts.PaymentApproved;
import com.inacio.mercurio.contracts.Topics;
import com.inacio.mercurio.ledger.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Reage a aprovacao do antifraude liquidando o pagamento no razao.
 *
 * <p>A idempotencia aqui nao depende de tabela de eventos processados: a
 * propria existencia de partidas para o {@code paymentId} ja diz que a
 * liquidacao ocorreu. Isso e mais forte, porque protege inclusive contra dois
 * eventos distintos que tentem liquidar o mesmo pagamento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementListener {

    private final SettlementService settlementService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = Topics.PAYMENT_APPROVED, groupId = "ledger-service")
    public void onApproved(@Payload PaymentApproved event) {
        SettlementService.SettlementOutcome outcome = settlementService.settle(event);

        if (outcome.duplicate()) {
            // Reentrega: o resultado ja foi publicado da primeira vez. Republicar
            // seria inofensivo (os consumidores deduplicam), mas e ruido evitavel.
            log.debug("Liquidacao do pagamento {} ja publicada anteriormente", event.paymentId());
            return;
        }

        kafkaTemplate.send(outcome.topic(), event.paymentId().toString(), outcome.event());
    }
}
