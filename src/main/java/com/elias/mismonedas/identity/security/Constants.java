package com.elias.mismonedas.identity.security;

public final class Constants {

    private Constants() {
    }

    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTHORITIES_CLAIM = "authorities";

    public static final String EMAIL_CLAIM = "email";
    public static final String NAME_CLAIM = "name";
    public static final String LASTNAME_CLAIM = "lastname";
    public static final String ROLE_CLAIM = "role";
    public static final String STATE_CLAIM = "state";
    public static final String LOCALE_CLAIM = "locale";
}
