package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.identity.domain.entity.RefreshToken;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.repository.RefreshTokenRepository;
import com.elias.mismonedas.identity.service.AuthTokenService;
import com.elias.mismonedas.identity.service.JwtTokenService;
import com.elias.mismonedas.shared.exception.InvalidRefreshTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthTokenServiceImpl implements AuthTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;

    @Value("${spring.security.jwt.expiration-seconds:3600}")
    private long accessExpirationSeconds;

    @Value("${spring.security.jwt.refresh-expiration-days:30}")
    private long refreshExpirationDays;

    @Override
    @Transactional
    public AuthResponse issue(User user) {
        if (isDisabled(user)) {
            throw new DisabledException("User account is not enabled");
        }
        String refreshToken = createRefreshToken(user, UUID.randomUUID().toString());
        return respond(user, refreshToken);
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String refreshToken) {
        RefreshToken current = lockByHash(refreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);

        OffsetDateTime now = OffsetDateTime.now();

        if (current.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (current.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException();
        }

        User user = current.getUser();
        if (isDisabled(user)) {
            throw new DisabledException("User account is not enabled");
        }

        String rotated = createRefreshToken(user, current.getFamilyId());
        current.setRevokedAt(now);
        current.setReplacedByHash(hash(rotated));

        return respond(user, rotated);
    }

    @Override
    @Transactional
    public void revoke(String refreshToken) {
        lockByHash(refreshToken)
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), OffsetDateTime.now()));
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, OffsetDateTime.now());
    }

    private AuthResponse respond(User user, String refreshToken) {
        return new AuthResponse(jwtTokenService.generateToken(user), refreshToken, accessExpirationSeconds);
    }

    private String createRefreshToken(User user, String familyId) {
        String raw = randomToken();
        OffsetDateTime now = OffsetDateTime.now();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(raw))
                .familyId(familyId)
                .expiresAt(now.plusDays(refreshExpirationDays))
                .createdAt(now)
                .build());
        return raw;
    }

    private Optional<RefreshToken> lockByHash(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHashForUpdate(hash(refreshToken));
    }

    private static boolean isDisabled(User user) {
        return user.getState() != null && !user.isEnabled();
    }

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
