package com.elias.mismonedas.identity.security;

import com.elias.mismonedas.identity.domain.enums.SupportedLocale;
import com.elias.mismonedas.shared.dto.ErrorResponse;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final JsonMessageCatalog messageCatalog;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse error = new ErrorResponse(
                "AUTHENTICATION_REQUIRED",
                request.getMethod(),
                request.getRequestURI(),
                messageCatalog.getMessage("error.authentication_required", SupportedLocale.toLocale(null)),
                HttpServletResponse.SC_UNAUTHORIZED,
                LocalDateTime.now().toString()
        );

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
