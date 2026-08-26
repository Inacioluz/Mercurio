package com.inacio.mercurio.payment.messaging;

import com.inacio.mercurio.payment.domain.OutboxEvent;
import com.inacio.mercurio.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Entrega ao Kafka os eventos gravados na outbox.
 *
 * <p>Roda em transacao propria e trava o lote com {@code FOR UPDATE SKIP LOCKED},
 * de modo que varias replicas do servico possam publicar em paralelo sem que
 * duas peguem o mesmo evento.
 *
 * <p>O envio e sincrono dentro da transacao de proposito: so marcamos
 * {@code published_at} depois que o broker confirma. Se o processo morre entre o
 * ack e o commit, o evento e reenviado no proximo ciclo — dai a deduplicacao por
 * {@code eventId} no consumidor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RETENTION = Duration.ofHours(24);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${mercurio.outbox.poll-interval:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.findPendingBatch(PageRequest.of(0, BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate
                        .send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                event.markFailed("Envio interrompido");
                break;
            } catch (Exception ex) {
                // Mantem pendente para a proxima rodada. Um broker fora do ar nao
                // pode derrubar o lote inteiro nem perder o evento.
                event.markFailed(ex.getMessage());
                log.warn("Falha ao publicar o evento {} no topico {} (tentativa {}): {}",
                        event.getId(), event.getTopic(), event.getAttempts(), ex.getMessage());
            }
        }

        outboxRepository.saveAll(pending);
    }

    /** Remove o que ja foi entregue ha bastante tempo, para a tabela nao crescer sem fim. */
    @Scheduled(cron = "${mercurio.outbox.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void cleanupPublished() {
        int removed = outboxRepository.deletePublishedBefore(Instant.now().minus(RETENTION));
        if (removed > 0) {
            log.info("Outbox: {} eventos ja entregues foram removidos", removed);
        }
    }
}
