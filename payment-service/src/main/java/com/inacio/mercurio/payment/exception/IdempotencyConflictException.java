package com.inacio.mercurio.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * A mesma Idempotency-Key foi reapresentada com um corpo diferente. Devolver o
 * pagamento original seria mentir sobre o que o cliente pediu; criar um novo
 * quebraria a promessa da chave. O certo e recusar.
 */
public class IdempotencyConflictException extends ApiException {

    public IdempotencyConflictException(String key) {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "A chave de idempotencia '" + key + "' ja foi usada com outro conteudo");
    }
}
