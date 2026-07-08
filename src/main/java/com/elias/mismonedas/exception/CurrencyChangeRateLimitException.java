package com.elias.mismonedas.exception;

public class CurrencyChangeRateLimitException extends RuntimeException {
    public CurrencyChangeRateLimitException(Long userId) {
        super("No puedes cambiar la moneda principal más de 2 veces al mes. userId=" + userId);
    }
}
