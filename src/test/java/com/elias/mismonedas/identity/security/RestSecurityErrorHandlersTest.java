package com.elias.mismonedas.identity.security;

import com.elias.mismonedas.shared.i18n.CurrentLocaleProvider;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RestSecurityErrorHandlersTest {

    private ObjectMapper objectMapper;
    private JsonMessageCatalog messageCatalog;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        messageCatalog = new JsonMessageCatalog(objectMapper);
        messageCatalog.initialize();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedErrorsShouldFallBackToSpanish() throws ServletException, IOException {
        RestAuthenticationEntryPoint handler = new RestAuthenticationEntryPoint(objectMapper, messageCatalog);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest("GET", "/api/v1/private"), response,
                new BadCredentialsException("credentials invalid"));

        var json = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(json.path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(json.path("message").asText())
                .isEqualTo("Se requiere autenticación o la sesión no es válida.");
    }

    @Test
    void accessDeniedErrorsShouldUseLocaleFromJwt() throws IOException {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("locale", "en-US")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        RestAccessDeniedHandler handler = new RestAccessDeniedHandler(
                objectMapper, messageCatalog, new CurrentLocaleProvider());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest("GET", "/api/v1/private"), response,
                new AccessDeniedException("permission denied"));

        var json = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(json.path("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(json.path("message").asText()).isEqualTo("You are not allowed to perform this action.");
    }
}
