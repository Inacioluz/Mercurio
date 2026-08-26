package com.inacio.mercurio.ledger.domain;

public enum AccountStatus {
    ACTIVE,
    BLOCKED,
    CLOSED;

    public boolean acceptsMovement() {
        return this == ACTIVE;
    }
}
