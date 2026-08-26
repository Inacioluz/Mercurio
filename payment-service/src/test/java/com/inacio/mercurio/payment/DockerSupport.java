package com.inacio.mercurio.payment;

import org.testcontainers.DockerClientFactory;

/**
 * Sem Docker os containers nao sobem. Em vez de quebrar o build de quem nao tem
 * o daemon rodando, a suite de integracao e pulada.
 */
public final class DockerSupport {

    private DockerSupport() {
    }

    public static boolean isAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
