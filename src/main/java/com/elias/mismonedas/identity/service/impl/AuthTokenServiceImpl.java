package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.identity.domain.entity.RefreshToken;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.repository.RefreshTokenRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.service.AuthTokenService;
import com.elias.mismonedas.identity.service.JwtTokenService;
import com.elias.mismonedas.shared.exception.InvalidRefreshTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthTokenServiceImpl implements AuthTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;

    @Value("${spring.security.jwt.expiration-seconds:900}")
    private long accessExpirationSeconds;

    @Value("${spring.security.jwt.refresh-expiration-days:30}")
    private long refreshExpirationDays;

    @Override
    @Transactional
    public AuthResponse issue(User user) {
        User currentUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new BadCredentialsException("User credentials are no longer valid"));
        if (!Objects.equals(currentUser.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("User credentials changed; authenticate again");
        }
        if (isDisabled(currentUser)) {
            throw new DisabledException("User account is not enabled");
        }
        String familyId = UUID.randomUUID().toString();
        String refreshToken = createRefreshToken(currentUser, familyId);
        return respond(currentUser, refreshToken, familyId);
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String refreshToken) {
        Long userId = findUserIdByHash(refreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(InvalidRefreshTokenException::new);
        RefreshToken current = lockByHash(refreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!Objects.equals(current.getUser().getId(), userId)) {
            throw new InvalidRefreshTokenException();
        }

        OffsetDateTime now = OffsetDateTime.now();

        if (current.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (current.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException();
        }

        if (isDisabled(user)) {
            throw new DisabledException("User account is not enabled");
        }

        current.setRevokedAt(now);
        refreshTokenRepository.flush();
        String rotated = createRefreshToken(user, current.getFamilyId());
        current.setReplacedByHash(hash(rotated));

        return respond(user, rotated, current.getFamilyId());
    }

    @Override
    @Transactional
    public void revoke(String refreshToken) {
        revokeAndGetUserId(refreshToken);
    }

    @Override
    @Transactional
    public Optional<Long> revokeAndGetUserId(String refreshToken) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<Long> observedUserId = findUserIdByHash(refreshToken);
        if (observedUserId.isEmpty()) {
            return Optional.empty();
        }
        Long userId = observedUserId.get();
        if (userRepository.findByIdForUpdate(userId).isEmpty()) {
            return Optional.empty();
        }
        Optional<RefreshToken> token = lockByHash(refreshToken);
        if (token.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken refreshTokenEntity = token.get();
        if (!Objects.equals(refreshTokenEntity.getUser().getId(), userId)) {
            return Optional.empty();
        }
        refreshTokenRepository.revokeFamily(refreshTokenEntity.getFamilyId(), now);
        if (refreshTokenEntity.getRevokedAt() != null
                || refreshTokenEntity.getExpiresAt() == null
                || !refreshTokenEntity.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(refreshTokenEntity.getUser().getId());
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long userId) {
        if (userRepository.findByIdForUpdate(userId).isEmpty()) {
            return;
        }
        refreshTokenRepository.revokeAllForUser(userId, OffsetDateTime.now());
    }

    private AuthResponse respond(User user, String refreshToken, String familyId) {
        return new AuthResponse(jwtTokenService.generateToken(user, familyId), refreshToken, accessExpirationSeconds);
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

    private Optional<Long> findUserIdByHash(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findUserIdByTokenHash(hash(refreshToken));
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
