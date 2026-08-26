package com.inacio.mercurio.antifraud.messaging;

import com.inacio.mercurio.antifraud.service.AntifraudService;
import com.inacio.mercurio.contracts.PaymentRequested;
import com.inacio.mercurio.contracts.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRequestedListener {

    private final AntifraudService antifraudService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = Topics.PAYMENT_REQUESTED, groupId = "antifraud-service")
    public void onRequested(@Payload PaymentRequested event) {
        AntifraudService.Outcome outcome = antifraudService.assess(event);

        if (outcome.replayed()) {
            log.debug("Decisao do pagamento {} ja publicada anteriormente", event.paymentId());
            return;
        }

        kafkaTemplate.send(outcome.topic(), event.paymentId().toString(), outcome.event());
    }
}
