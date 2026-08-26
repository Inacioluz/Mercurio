package com.inacio.mercurio.antifraud.config;

import com.inacio.mercurio.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.ArrayList;
import java.util.List;

/**
 * Declara todos os topicos do sistema, inclusive os que este servico nao usa.
 *
 * <p>Parece redundante, mas nao e: o auto-create do broker esta desligado, e sem
 * esta declaracao um consumidor que suba antes do produtor encontraria o topico
 * inexistente. Pior, com auto-create ligado o broker o criaria com uma unica
 * particao — as demais ficariam sem consumidor e os eventos que caissem nelas
 * jamais seriam processados. Declarando em todos, quem subir primeiro cria com
 * a configuracao correta e os demais apenas confirmam.
 *
 * <p>O {@link KafkaAdmin} tambem acrescenta particoes a um topico que exista com
 * menos do que o declarado, o que conserta um ambiente ja criado errado.
 */
@Configuration
public class TopicsConfig {

    @Bean
    public KafkaAdmin.NewTopics mercurioTopics() {
        List<NewTopic> topics = new ArrayList<>();
        for (String topic : Topics.all()) {
            topics.add(build(topic));
            // A DLT precisa existir antes da primeira mensagem envenenada:
            // cria-la sob pressao, no meio de uma falha, e o pior momento.
            topics.add(build(Topics.deadLetter(topic)));
        }
        return new KafkaAdmin.NewTopics(topics.toArray(new NewTopic[0]));
    }

    private NewTopic build(String name) {
        return TopicBuilder.name(name)
                .partitions(Topics.PARTITIONS)
                .replicas(Topics.REPLICAS)
                .build();
    }
}
