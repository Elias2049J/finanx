package com.elias.mismonedas.shared.adapter.ai.gemini;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.client.RestClient;



@Configuration
public class GeminiConfig {





    @Bean("geminiRestClientBuilder")
    public RestClient.Builder geminiRestClientBuilder() {
        return RestClient.builder();
    }
}