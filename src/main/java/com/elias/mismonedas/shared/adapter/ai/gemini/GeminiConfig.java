package com.elias.mismonedas.shared.adapter.ai.gemini;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.client.RestClient;

/**
 * Configuracion  para Gemini Interactions API.
*/
@Configuration
public class GeminiConfig {

    /**
     * Expone un {@link RestClient.Builder} dedicado para Gemini,
     * permitiendo que otros RestClients del proyecto no compartan
     * la misma configuracion de base URL o headers.
     */
    @Bean("geminiRestClientBuilder")
    public RestClient.Builder geminiRestClientBuilder() {
        return RestClient.builder();
    }
}