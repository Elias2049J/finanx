package com.elias.finanx.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.elias.finanx.dto.ErrorResponse;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                accessDeniedException.getMessage(),
                HttpServletResponse.SC_FORBIDDEN,
                LocalDateTime.now().toString()
        );

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
