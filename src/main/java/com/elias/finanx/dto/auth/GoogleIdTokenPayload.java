package com.elias.finanx.dto.auth;

public record GoogleIdTokenPayload(
        String email,
        String givenName,
        String familyName
) {
}

