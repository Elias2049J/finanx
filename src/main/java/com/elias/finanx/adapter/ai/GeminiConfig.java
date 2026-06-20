package com.elias.finanx.adapter.ai;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Bean
    public Client geminiClient() {
        return new Client.Builder()
                .apiKey(apiKey)
                .build();
    }
}