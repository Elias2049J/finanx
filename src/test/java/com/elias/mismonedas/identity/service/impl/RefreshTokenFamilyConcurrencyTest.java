package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.Role;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.dto.user.UserRequest;
import com.elias.mismonedas.identity.repository.RefreshTokenRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.service.AuthTokenService;
import com.elias.mismonedas.identity.service.UserService;
import com.elias.mismonedas.shared.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.messaging.rabbitmq.consumer-auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
class RefreshTokenFamilyConcurrencyTest {

    @Autowired private AuthTokenService authTokenService;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;

    @AfterEach
    void deleteTestUser() {
        if (user != null && user.getId() != null) {
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void logoutOfOlderRotatedTokenCannotMissConcurrentRefresh() throws Exception {
        createUser();
        AuthResponse first = authTokenService.issue(user);
        AuthResponse current = authTokenService.refresh(first.getRefreshToken());

        race(
                () -> refreshIgnoringReplay(current.getRefreshToken()),
                () -> authTokenService.revokeAndGetUserId(first.getRefreshToken())
        );

        assertNoActiveRefreshTokens();
    }

    @Test
    void passwordChangeCannotMissRefreshCommittedConcurrently() throws Exception {
        createUser();
        AuthResponse first = authTokenService.issue(user);

        race(
                () -> refreshIgnoringReplay(first.getRefreshToken()),
                this::changePassword
        );

        assertNoActiveRefreshTokens();
    }

    private void createUser() {
        user = new User();
        user.setName("Session race");
        user.setLastname("Test");
        user.setEmail("session-race-" + UUID.randomUUID() + "@example.test");
        user.setPassword(passwordEncoder.encode("original-password"));
        user.setRole(Role.USER);
        user = userRepository.saveAndFlush(user);
    }

    private void refreshIgnoringReplay(String refreshToken) {
        try {
            authTokenService.refresh(refreshToken);
        } catch (InvalidRefreshTokenException ignored) {
            // A logout/password change that wins the user lock invalidates this attempt.
        }
    }

    private void changePassword() {
        UserRequest request = new UserRequest(
                user.getName(), user.getLastname(), user.getEmail(), "replacement-password");
        userService.update(user.getId(), request);
    }

    private void race(Runnable refresh, Runnable revoke) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<?> refreshTask = executor.submit(() -> {
            start.await();
            refresh.run();
            return null;
        });
        Future<?> revokeTask = executor.submit(() -> {
            start.await();
            revoke.run();
            return null;
        });

        try {
            start.countDown();
            refreshTask.get(15, TimeUnit.SECONDS);
            revokeTask.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertNoActiveRefreshTokens() {
        assertThat(refreshTokenRepository.countByUser_IdAndRevokedAtIsNull(user.getId()))
                .as("Active refresh tokens for test user")
                .isZero();
    }
}
