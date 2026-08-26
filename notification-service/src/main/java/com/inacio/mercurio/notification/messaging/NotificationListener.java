package com.inacio.mercurio.notification.messaging;

import com.inacio.mercurio.contracts.PaymentFailed;
import com.inacio.mercurio.contracts.PaymentRejected;
import com.inacio.mercurio.contracts.PaymentSettled;
import com.inacio.mercurio.contracts.Topics;
import com.inacio.mercurio.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ouve os desfechos da saga e avisa as partes.
 *
 * <p>So os estados terminais geram notificacao: avisar a cada passo intermediario
 * encheria a caixa do cliente com ruido sobre um processo que dura milissegundos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = Topics.PAYMENT_SETTLED, groupId = "notification-service")
    public void onSettled(@Payload PaymentSettled event) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("amount", event.amount());
        metadata.put("ledgerTransactionId", event.ledgerTransactionId());
        metadata.put("counterparty", event.payeeAccount());

        notificationService.notify(event.eventId(), event.paymentId(), event.payerAccount(),
                "PAYMENT_SETTLED",
                "Pagamento concluido",
                "Enviamos %s para a conta %s. Saldo apos a operacao: %s."
                        .formatted(event.amount(), event.payeeAccount(), event.payerBalanceAfter()),
                metadata);

        Map<String, Object> payeeMetadata = new LinkedHashMap<>(metadata);
        payeeMetadata.put("counterparty", event.payerAccount());

        notificationService.notify(event.eventId(), event.paymentId(), event.payeeAccount(),
                "PAYMENT_SETTLED",
                "Voce recebeu um pagamento",
                "A conta %s enviou %s para voce.".formatted(event.payerAccount(), event.amount()),
                payeeMetadata);
    }

    @KafkaListener(topics = Topics.PAYMENT_REJECTED, groupId = "notification-service")
    public void onRejected(@Payload PaymentRejected event) {
        // O evento de recusa nao carrega as contas — o antifraude barra antes de
        // qualquer movimentacao, e o payment-service e quem tem o cadastro. Aqui
        // a notificacao fica indexada pelo pagamento.
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("riskScore", event.riskScore());
        metadata.put("reasons", event.reasons());

        notificationService.notify(event.eventId(), event.paymentId(), "payment:" + event.paymentId(),
                "PAYMENT_REJECTED",
                "Pagamento nao autorizado",
                "O pagamento foi barrado pela analise de risco. Motivos: %s."
                        .formatted(joinReasons(event.reasons())),
                metadata);
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED, groupId = "notification-service")
    public void onFailed(@Payload PaymentFailed event) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reasonCode", event.reasonCode());

        notificationService.notify(event.eventId(), event.paymentId(), "payment:" + event.paymentId(),
                "PAYMENT_FAILED",
                "Nao foi possivel concluir o pagamento",
                event.reason(),
                metadata);
    }

    private String joinReasons(List<String> reasons) {
        return reasons == null || reasons.isEmpty() ? "nao informados" : String.join("; ", reasons);
    }
}
