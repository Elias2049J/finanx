package com.elias.mismonedas.identity.util;

public class InvalidEmailAddressException extends IllegalArgumentException {
    public InvalidEmailAddressException() {
        super("A single valid email address is required");
    }
}
