package com.elias.mismonedas.shared.config;

import com.elias.mismonedas.billing.exception.InvalidWebhookSignatureException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnUnauthorizedForInvalidWebhookSignature() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/MANUAL_TRANSFER");

        var response = handler.handleAuthentication(
                new InvalidWebhookSignatureException("Invalid signature"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
