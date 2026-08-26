package com.inacio.mercurio.antifraud.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * O indice unico em {@code paymentId} vem da anotacao {@code @Indexed} na
 * entidade; {@code auto-index-creation} esta ligado no application.yml para que
 * ele seja criado no start. Em producao com colecoes grandes, o indice seria
 * criado por migracao — aqui a criacao automatica mantem o ambiente reproduzivel.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
