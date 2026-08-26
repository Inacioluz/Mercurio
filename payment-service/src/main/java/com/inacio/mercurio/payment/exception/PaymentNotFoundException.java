package com.inacio.mercurio.payment.exception;

import org.springframework.http.HttpStatus;

public class PaymentNotFoundException extends ApiException {

    public PaymentNotFoundException(Object id) {
        super(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Pagamento nao encontrado: " + id);
    }
}
