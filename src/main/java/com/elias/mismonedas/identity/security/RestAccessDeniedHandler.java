package com.elias.mismonedas.identity.security;

import com.elias.mismonedas.shared.dto.ErrorResponse;
import com.elias.mismonedas.shared.i18n.CurrentLocaleProvider;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final JsonMessageCatalog messageCatalog;
    private final CurrentLocaleProvider localeProvider;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse error = new ErrorResponse(
                "ACCESS_DENIED",
                request.getMethod(),
                request.getRequestURI(),
                messageCatalog.getMessage("error.access_denied", localeProvider.currentLocale()),
                HttpServletResponse.SC_FORBIDDEN,
                LocalDateTime.now().toString()
        );

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
