package com.elias.mismonedas.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${app.http.connect-timeout:5s}")
    private Duration connectTimeout;

    @Value("${app.http.read-timeout:10s}")
    private Duration readTimeout;

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(HttpRequestFactories.create(connectTimeout, readTimeout));
    }
}
