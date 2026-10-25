package com.elias.mismonedas.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

public class AuthRouteBearerTokenResolver implements BearerTokenResolver {

    private static final String AUTH_PATH = "/api/v1/auth";

    private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getServletPath();
        if (AUTH_PATH.equals(path) || path.startsWith(AUTH_PATH + "/")) {
            return null;
        }
        return delegate.resolve(request);
    }
}
