package com.inacio.mercurio.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Mensageria do payment-service.
 *
 * <p>O conversor deriva o tipo do proprio parametro do metodo anotado com
 * {@code @KafkaListener}, o que dispensa cabecalho de tipo na mensagem e mantem
 * o produtor livre para ser escrito em qualquer linguagem.
 */
@Slf4j
@Configuration
public class KafkaConfig {

    @Bean
    public StringJsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new StringJsonMessageConverter(objectMapper);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            StringJsonMessageConverter messageConverter,
            KafkaTemplate<String, String> kafkaTemplate) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setRecordMessageConverter(messageConverter);
        factory.setConcurrency(3);
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));
        return factory;
    }

    /**
     * Tenta de novo com espera crescente e, esgotadas as tentativas, desvia a
     * mensagem para {@code <topico>.DLT} em vez de bloquear a particao. Uma
     * mensagem envenenada nao pode travar o fluxo dos demais pagamentos.
     */
    private DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> {
                    log.error("Evento enviado para a DLT. topico={} particao={} offset={} causa={}",
                            record.topic(), record.partition(), record.offset(), exception.getMessage());
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                });

        var backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(10_000L);

        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.setLogLevel(org.springframework.kafka.KafkaException.Level.WARN);
        return handler;
    }
}
