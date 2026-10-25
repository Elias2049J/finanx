package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.identity.domain.entity.RefreshToken;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.RefreshTokenRepository;
import com.elias.mismonedas.identity.service.JwtTokenService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthTokenServiceImplTest {

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final AuthTokenServiceImpl service = new AuthTokenServiceImpl(refreshTokenRepository, jwtTokenService);

    @Test
    void shouldResolveRefreshTokenOwnerWhileRevokingItsFamily() throws Exception {
        User owner = new User();
        owner.setId(81L);
        RefreshToken refreshToken = RefreshToken.builder()
                .user(owner)
                .familyId("device-session-family")
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .build();
        when(refreshTokenRepository.findByTokenHashForUpdate(hash("refresh-token")))
                .thenReturn(Optional.of(refreshToken));

        Optional<Long> userId = service.revokeAndGetUserId("refresh-token");

        assertThat(userId).contains(81L);
        verify(refreshTokenRepository).revokeFamily(eq("device-session-family"), any());
    }

    @Test
    void shouldNotResolveOwnerOrRevokeFamilyForUnknownRefreshToken() throws Exception {
        when(refreshTokenRepository.findByTokenHashForUpdate(hash("unknown-token")))
                .thenReturn(Optional.empty());

        assertThat(service.revokeAndGetUserId("unknown-token")).isEmpty();

        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    void shouldRevokeExpiredTokenFamilyWithoutUsingItToDeleteAnFcmToken() throws Exception {
        RefreshToken expired = RefreshToken.builder()
                .user(user(82L))
                .familyId("expired-family")
                .expiresAt(OffsetDateTime.now().minusSeconds(1))
                .build();
        when(refreshTokenRepository.findByTokenHashForUpdate(hash("expired-token")))
                .thenReturn(Optional.of(expired));

        assertThat(service.revokeAndGetUserId("expired-token")).isEmpty();

        verify(refreshTokenRepository).revokeFamily(eq("expired-family"), any());
    }

    @Test
    void shouldRevokeAlreadyRevokedTokenFamilyWithoutReturningItsOwner() throws Exception {
        RefreshToken revoked = RefreshToken.builder()
                .user(user(83L))
                .familyId("revoked-family")
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .revokedAt(OffsetDateTime.now().minusMinutes(1))
                .build();
        when(refreshTokenRepository.findByTokenHashForUpdate(hash("revoked-token")))
                .thenReturn(Optional.of(revoked));

        assertThat(service.revokeAndGetUserId("revoked-token")).isEmpty();

        verify(refreshTokenRepository).revokeFamily(eq("revoked-family"), any());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
