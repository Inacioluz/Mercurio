package com.inacio.mercurio.payment.messaging;

import com.inacio.mercurio.payment.domain.ProcessedEvent;
import com.inacio.mercurio.payment.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Descarta reentregas do mesmo evento.
 *
 * <p>A marcacao roda em transacao propria e commitada <b>antes</b> do
 * processamento. A escolha importa: se o registro fosse gravado na mesma
 * transacao do efeito, uma falha depois do efeito colateral externo deixaria o
 * evento marcado como nao processado e ele seria reexecutado. Marcando antes,
 * uma falha no processamento perde o evento em vez de duplica-lo — para
 * dinheiro, deixar de creditar duas vezes vale mais do que reprocessar, e o
 * estado fica visivelmente parado para investigacao.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDeduplicator {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * @return true se este e o primeiro encontro com o evento; false se ja foi
     *         processado antes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(UUID eventId, String topic) {
        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(eventId, topic));
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.debug("Evento {} do topico {} ja processado, descartando", eventId, topic);
            return false;
        }
    }
}
