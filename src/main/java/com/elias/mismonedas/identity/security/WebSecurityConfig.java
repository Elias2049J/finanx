package com.elias.mismonedas.identity.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final ActiveUserFilter activeUserFilter;

    @Value("${app.front-url}")
    private String frontUrl;

    @Value("${app.front-url-https:https://localhost:4200}")
    private String frontUrlHttps;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                   Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter) {
        http
                .cors(cors -> {
                })
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/google",
                                "/api/v1/auth/register",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/v3/api-docs.yaml",
                                "/swagger-ui.html"
                        ).permitAll()

                        .requestMatchers("/api/v1/users/{id}/delete").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/users/{id}/disable").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/users/{id}/reset-data").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/users/search").hasAnyRole("SYSTEM")
                        .requestMatchers("/api/v1/users").hasAnyRole("SYSTEM")
                        .requestMatchers("/api/v1/transactions/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/reasons/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/budgets/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/savings/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/schedule/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/wallets/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/categories/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/payments/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/subscriptions/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/reports/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/notifications/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/users/{userId}/preferences/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/fcm-tokens/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/chat/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/analytics/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/receipts/**").hasAnyRole("USER", "SYSTEM")
                        .requestMatchers("/api/v1/images/**").hasAnyRole("USER", "SYSTEM")


                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt ->
                                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                )
                .addFilterAfter(activeUserFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    FilterRegistrationBean<ActiveUserFilter> activeUserFilterRegistration() {
        FilterRegistrationBean<ActiveUserFilter> registration = new FilterRegistrationBean<>(activeUserFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(frontUrl, frontUrlHttps));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
