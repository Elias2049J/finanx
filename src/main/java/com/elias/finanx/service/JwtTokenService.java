package com.elias.finanx.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public interface JwtTokenService {
    String generateToken(UserDetails user);

    DecodedJWT verify(String token);

    Collection<String> extractAuthorities(DecodedJWT jwt);
}
