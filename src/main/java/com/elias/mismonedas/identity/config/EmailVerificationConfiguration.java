package com.elias.mismonedas.identity.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class EmailVerificationConfiguration {

    @Bean
    SecureRandom emailVerificationSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    Clock emailVerificationClock() {
        return Clock.systemUTC();
    }

    @Bean
    RateLimiter emailVerificationRateLimiter(
            @Value("${app.email-verification.global-send-limit-per-minute:10}") int permitsPerMinute) {
        if (permitsPerMinute < 1) {
            throw new IllegalStateException("Global email verification rate limit must be positive");
        }
        RateLimiterConfig rateLimitConfig = RateLimiterConfig.custom()
                .limitForPeriod(permitsPerMinute)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build();
        return RateLimiter.of("email-verification", rateLimitConfig);
    }
}
