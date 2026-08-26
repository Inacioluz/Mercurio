package com.inacio.mercurio.notification.service;

import com.inacio.mercurio.notification.domain.Notification;
import com.inacio.mercurio.notification.domain.NotificationChannel;
import com.inacio.mercurio.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Grava as notificacoes das partes envolvidas.
 *
 * <p>Nao ha envio real de e-mail ou SMS: o servico registra o que seria enviado
 * e expoe por API. Integrar um provedor de verdade acrescentaria credenciais e
 * um ponto de falha externo sem mudar nada da arquitetura que o projeto se
 * propoe a demonstrar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Registra uma notificacao, ignorando silenciosamente a repeticao — o indice
     * composto {@code (eventId, recipientAccount)} e quem garante que o mesmo
     * evento nao gere dois avisos para a mesma conta.
     */
    public void notify(UUID eventId, UUID paymentId, String recipientAccount,
                       String type, String subject, String body, Map<String, Object> metadata) {

        if (notificationRepository.existsByEventIdAndRecipientAccount(eventId, recipientAccount)) {
            log.debug("Notificacao de {} para {} ja registrada", eventId, recipientAccount);
            return;
        }

        try {
            notificationRepository.save(Notification.builder()
                    .eventId(eventId)
                    .paymentId(paymentId)
                    .recipientAccount(recipientAccount)
                    .channel(channelFor(type))
                    .type(type)
                    .subject(subject)
                    .body(body)
                    .metadata(metadata)
                    .createdAt(Instant.now())
                    .build());

            log.info("Notificacao {} registrada para {} (pagamento {})", type, recipientAccount, paymentId);
        } catch (DuplicateKeyException ex) {
            // Duas entregas simultaneas do mesmo evento; a primeira ja gravou.
            log.debug("Notificacao concorrente de {} para {} descartada", eventId, recipientAccount);
        }
    }

    /**
     * Escolhe o canal pela urgencia do aviso: falha e recusa pedem imediatismo,
     * confirmacao de sucesso pode esperar o e-mail.
     */
    private NotificationChannel channelFor(String type) {
        return switch (type) {
            case "PAYMENT_FAILED", "PAYMENT_REJECTED" -> NotificationChannel.PUSH;
            default -> NotificationChannel.EMAIL;
        };
    }
}
