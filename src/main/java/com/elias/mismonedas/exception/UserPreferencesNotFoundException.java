package com.elias.mismonedas.exception;

public class UserPreferencesNotFoundException extends RuntimeException {

    public UserPreferencesNotFoundException(Long userId) {
        super("Preferences not found for user " + userId);
    }
}
