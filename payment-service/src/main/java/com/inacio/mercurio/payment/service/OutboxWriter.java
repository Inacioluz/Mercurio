package com.inacio.mercurio.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inacio.mercurio.contracts.DomainEvent;
import com.inacio.mercurio.payment.domain.OutboxEvent;
import com.inacio.mercurio.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Serializa um evento de dominio e o enfileira na outbox. Deve ser chamado
 * dentro da transacao que altera o estado correspondente.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void enqueue(String topic, DomainEvent event) {
        outboxRepository.save(OutboxEvent.builder()
                .topic(topic)
                .messageKey(event.paymentId().toString())
                .payload(serialize(event))
                .createdAt(Instant.now())
                .attempts(0)
                .build());
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            // Um evento que nao serializa e um defeito de programacao; deixar a
            // transacao falhar e melhor do que gravar um payload invalido.
            throw new IllegalStateException("Falha ao serializar o evento " + event.getClass().getSimpleName(), ex);
        }
    }
}
