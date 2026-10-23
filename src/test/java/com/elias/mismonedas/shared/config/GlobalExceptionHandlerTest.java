package com.elias.mismonedas.shared.config;

import com.elias.mismonedas.billing.exception.InvalidWebhookSignatureException;
import com.elias.mismonedas.shared.i18n.CurrentLocaleProvider;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.http.HttpException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        JsonMessageCatalog catalog = new JsonMessageCatalog(new ObjectMapper());
        catalog.initialize();
        handler = new GlobalExceptionHandler(catalog, new CurrentLocaleProvider());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnUnauthorizedForInvalidWebhookSignature() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/MANUAL_TRANSFER");

        var response = handler.handleAuthentication(
                new InvalidWebhookSignatureException("Invalid signature"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getCode()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(response.getBody().getMessage())
                .isEqualTo("Se requiere autenticación o la sesión no es válida.");
    }

    @Test
    void shouldUseLocaleFromJwtForApiErrors() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("locale", "en-US")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        HttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/users/me/preferences");

        var response = handler.handleIllegalArgument(new IllegalArgumentException("internal message"), request);

        assertThat(response.getBody().getCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.getBody().getMessage()).isEqualTo("The request contains invalid data.");
    }

    @Test
    void shouldKeepSpecificLocalizedMessageForDuplicateFcmToken() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/fcm-tokens");

        var response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("uq_fcm_token_user_token"), request);

        assertThat(response.getBody().getCode()).isEqualTo("FCM_TOKEN_ALREADY_REGISTERED");
        assertThat(response.getBody().getMessage())
                .isEqualTo("El token de notificaciones ya está registrado para este usuario.");
    }

    @Test
    void shouldReturnGenericServerErrorForIoException() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/receipts/scan");

        var response = handler.handleIoException(new IOException("disk detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Ocurrió un error interno.");
    }

    @Test
    void shouldReturnGenericServerErrorForHttpException() {
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/analytics/dashboard/balance");

        var response = handler.handleHttpException(new HttpException("upstream detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Ocurrió un error interno.");
    }
}
