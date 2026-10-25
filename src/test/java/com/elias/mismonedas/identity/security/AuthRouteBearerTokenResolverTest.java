package com.elias.mismonedas.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRouteBearerTokenResolverTest {

    private final AuthRouteBearerTokenResolver resolver = new AuthRouteBearerTokenResolver();

    @Test
    void shouldIgnoreStaleBearerTokensOnPublicAuthRoutes() {
        MockHttpServletRequest request = request("/api/v1/auth/refresh");

        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void shouldContinueResolvingBearerTokensOnProtectedRoutes() {
        MockHttpServletRequest request = request("/api/v1/users/me");

        assertThat(resolver.resolve(request)).isEqualTo("stale-access-token");
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.addHeader("Authorization", "Bearer stale-access-token");
        return request;
    }
}
