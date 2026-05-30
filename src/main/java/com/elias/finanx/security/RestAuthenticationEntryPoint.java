package com.elias.finanx.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.elias.finanx.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                authException.getMessage(),
                HttpServletResponse.SC_UNAUTHORIZED,
                LocalDateTime.now().toString()
        );

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
