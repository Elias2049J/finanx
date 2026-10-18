package com.elias.mismonedas.identity.security;

public final class Constants {

    private Constants() {
    }

    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTHORITIES_CLAIM = "authorities";

    // F11 (token self-sufficient): con el usuario vivo en el token, un request autenticado
    // no necesita tocar UserRepository. Ninguno de estos datos es autorizacion: `role` va en
    // `authorities`, y `plan` deliberadamente NO entra aqui (es volatile).
    public static final String EMAIL_CLAIM = "email";
    public static final String ROLE_CLAIM = "role";
    public static final String STATE_CLAIM = "state";
    public static final String TIMEZONE_CLAIM = "tz";
    public static final String CURRENCY_CLAIM = "cc";
}
