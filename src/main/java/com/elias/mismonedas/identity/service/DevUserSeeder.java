package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.AuthProvider;
import com.elias.mismonedas.identity.domain.enums.Role;
import com.elias.mismonedas.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea (una sola vez) un usuario local ya verificado para facilitar las pruebas
 * manuales en desarrollo. Se activa explicitamente con
 * {@code APP_DEV_SEED_USER_ENABLED=true}; en produccion no debe activarse.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.dev.seed-user", name = "enabled", havingValue = "true")
public class DevUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${app.dev.seed-user.email:}")
    private String email;

    @Value("${app.dev.seed-user.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "APP_DEV_SEED_USER_ENABLED=true requiere APP_DEV_SEED_USER_EMAIL y APP_DEV_SEED_USER_PASSWORD");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            log.info("Usuario de desarrollo ya existe: {}", normalizedEmail);
            return;
        }

        User user = new User();
        user.setName("Dev");
        user.setLastname("User");
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        user.setRole(Role.USER);
        userRepository.save(user);

        log.warn("Usuario de desarrollo creado: {} (solo para desarrollo, no usar en produccion)", normalizedEmail);
    }
}