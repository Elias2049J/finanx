package com.elias.mismonedas.shared.config;

import io.swagger.v3.core.jackson.ModelResolver;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @PostConstruct
    void init() {
        ModelResolver.enumsAsRef = true;
    }
}