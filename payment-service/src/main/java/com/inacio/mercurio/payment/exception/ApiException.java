package com.inacio.mercurio.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Excecao base: carrega o status HTTP e um codigo de erro estavel. */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
