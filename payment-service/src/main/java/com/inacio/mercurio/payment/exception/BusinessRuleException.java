package com.inacio.mercurio.payment.exception;

import org.springframework.http.HttpStatus;

/** Requisicao valida que viola uma regra de negocio. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String errorCode, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, message);
    }

    public static BusinessRuleException sameAccount() {
        return new BusinessRuleException("SAME_ACCOUNT_PAYMENT",
                "A conta de origem e destino devem ser diferentes");
    }
}
