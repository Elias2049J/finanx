package com.elias.finanx.adapter.ai.gemini;

import com.elias.finanx.adapter.ai.gemini.dto.ErrorResponse;
import com.elias.finanx.adapter.ai.gemini.dto.InteractionRequest;
import com.elias.finanx.adapter.ai.gemini.dto.InteractionResponse;
import com.elias.finanx.adapter.ai.gemini.exception.GeminiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionClient {

    @Value("${app.gemini.base-url}")
    private String baseUrl;

    private final RestClient.Builder builder;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key}")
    private String apiKey;

    private RestClient restClient;

    @PostConstruct
    private void init() {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    public InteractionResponse create(InteractionRequest request) {
        log.debug("[GEMINI-CLIENT] POST /interactions model={} prevId={}",
                request.model(), request.previousInteractionId());

        return restClient.post()
                .uri("/interactions")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    byte[] body = res.getBody().readAllBytes();
                    throw mapError(res.getStatusCode().value(), body);
                })
                .body(InteractionResponse.class);
    }

    public InteractionResponse get(String interactionId) {
        log.debug("[GEMINI-CLIENT] GET /interactions/{}", interactionId);

        return restClient.get()
                .uri("/interactions/{id}", interactionId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    byte[] body = res.getBody().readAllBytes();
                    throw mapError(res.getStatusCode().value(), body);
                })
                .body(InteractionResponse.class);
    }

    public void delete(String interactionId) {
        log.debug("[GEMINI-CLIENT] DELETE /interactions/{}", interactionId);

        restClient.delete()
                .uri("/interactions/{id}", interactionId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    byte[] body = res.getBody().readAllBytes();
                    throw mapError(res.getStatusCode().value(), body);
                })
                .toBodilessEntity();
    }

    private GeminiException mapError(int statusCode, byte[] body) {
        String raw = new String(body);
        String message;

        try {
            ErrorResponse err = objectMapper.readValue(raw, ErrorResponse.class);
            message = "[%s] %s".formatted(err.status(), err.message());
        } catch (Exception e) {
            message = "HTTP %d — %s".formatted(statusCode, raw);
        }

        log.warn("[GEMINI-CLIENT] Error HTTP {}: {}", statusCode, message);

        return switch (statusCode) {
            case 400 -> new GeminiException.InvalidRequestException(message);
            case 401, 403 -> new GeminiException.AuthException(message);
            case 429 -> new GeminiException.RateLimitException(message);
            case 502, 503, 504 -> new GeminiException.UnavailableException(message);
            default -> new GeminiException(message, statusCode);
        };
    }
}