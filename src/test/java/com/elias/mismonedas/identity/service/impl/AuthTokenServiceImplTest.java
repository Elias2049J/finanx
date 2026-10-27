package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.identity.domain.entity.RefreshToken;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.UserState;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.repository.RefreshTokenRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.service.JwtTokenService;
import com.elias.mismonedas.shared.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthTokenServiceImplTest {

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final AuthTokenServiceImpl service =
            new AuthTokenServiceImpl(refreshTokenRepository, userRepository, jwtTokenService);

    @Test
    void shouldBindIssuedAccessTokenToTheCreatedRefreshFamily() {
        User user = user(80L);
        user.setPassword("same-password-hash");
        when(userRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(user));
        when(jwtTokenService.generateToken(any(UserDetails.class), anyString())).thenReturn("access-token");

        AuthResponse response = service.issue(user);

        var lockOrder = inOrder(userRepository, refreshTokenRepository);
        lockOrder.verify(userRepository).findByIdForUpdate(80L);
        lockOrder.verify(refreshTokenRepository).save(any(RefreshToken.class));
        ArgumentCaptor<RefreshToken> refreshToken = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshToken.capture());
        verify(jwtTokenService).generateToken(user, refreshToken.getValue().getFamilyId());
        assertThat(response.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void shouldRejectAnAuthenticationSnapshotIfPasswordChangedBeforeSessionIssuance() {
        User authenticatedSnapshot = user(80L);
        authenticatedSnapshot.setPassword("old-password-hash");
        User currentUser = user(80L);
        currentUser.setPassword("new-password-hash");
        when(userRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(currentUser));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.issue(authenticatedSnapshot))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(jwtTokenService, never()).generateToken(any(UserDetails.class), anyString());
    }

    @Test
    void shouldNotIssueTokensWhileDeletionIsPendingEvenDuringGracePeriod() {
        User pendingDeletion = user(80L);
        pendingDeletion.setPassword("same-password-hash");
        pendingDeletion.requestDeletion(OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusDays(6));
        when(userRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(pendingDeletion));

        assertThatThrownBy(() -> service.issue(pendingDeletion))
                .isInstanceOf(DisabledException.class);

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(jwtTokenService, never()).generateToken(any(UserDetails.class), anyString());
    }

    @Test
    void shouldRejectPendingDeletionWhenPersistedUserStateIsNull() {
        User pendingDeletion = user(80L);
        pendingDeletion.setPassword("same-password-hash");
        pendingDeletion.requestDeletion(OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusDays(6));
        pendingDeletion.setState(null);
        when(userRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(pendingDeletion));

        assertThatThrownBy(() -> service.issue(pendingDeletion))
                .isInstanceOf(DisabledException.class);

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(jwtTokenService, never()).generateToken(any(UserDetails.class), anyString());
    }

    @Test
    void shouldKeepTheRefreshFamilyWhenRotatingAccessToken() throws Exception {
        User user = user(80L);
        user.setPassword("same-password-hash");
        RefreshToken current = RefreshToken.builder()
                .user(user)
                .familyId("same-device-family")
                .tokenHash(hash("refresh-token"))
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .build();
        when(refreshTokenRepository.findUserIdByTokenHash(hash("refresh-token")))
                .thenReturn(Optional.of(80L));
        when(userRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash("refresh-token")))
                .thenReturn(Optional.of(current));
        when(jwtTokenService.generateToken(any(UserDetails.class), anyString())).thenReturn("rotated-access-token");

        AuthResponse response = service.refresh("refresh-token");

        var lockOrder = inOrder(refreshTokenRepository, userRepository);
        lockOrder.verify(refreshTokenRepository).findUserIdByTokenHash(hash("refresh-token"));
        lockOrder.verify(userRepository).findByIdForUpdate(80L);
        lockOrder.verify(refreshTokenRepository).findByTokenHashForUpdate(hash("refresh-token"));
        lockOrder.verify(refreshTokenRepository).flush();
        lockOrder.verify(refreshTokenRepository).save(any(RefreshToken.class));
        ArgumentCaptor<RefreshToken> rotated = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(rotated.capture());
        verify(jwtTokenService).generateToken(user, "same-device-family");
        assertThat(rotated.getValue().getFamilyId()).isEqualTo("same-device-family");
        assertThat(response.getAccessToken()).isEqualTo("rotated-access-token");
    }

    @Test
    void shouldRevokeFamilyWhenRotatedRefreshTokenIsReused() throws Exception {
        User user = user(80L);
        RefreshToken replayed = RefreshToken.builder()
                .user(user)
                .familyId("same-device-family")
                .tokenHash(hash("replayed-refresh-token"))
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .revokedAt(OffsetDateTime.now().minusSeconds(1))
                .build();
        when(refreshTokenRepository.findUserIdByTokenHash(hash("replayed-refresh-token")))
                .thenReturn(Optional.of(80L));
        when(userRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash("replayed-refresh-token")))
                .thenReturn(Optional.of(replayed));

        assertThatThrownBy(() -> service.refresh("replayed-refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository).revokeFamily(eq("same-device-family"), any());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void shouldResolveRefreshTokenOwnerWhileRevokingItsFamily() throws Exception {
        User owner = new User();
        owner.setId(81L);
        owner.setPassword("password-hash");
        RefreshToken refreshToken = RefreshToken.builder()
                .user(owner)
                .familyId("device-session-family")
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .build();
        when(refreshTokenRepository.findUserIdByTokenHash(hash("refresh-token")))
                .thenReturn(Optional.of(81L));
        when(userRepository.findByIdForUpdate(81L)).thenReturn(Optional.of(owner));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash("refresh-token")))
                .thenReturn(Optional.of(refreshToken));

        Optional<Long> userId = service.revokeAndGetUserId("refresh-token");

        assertThat(userId).contains(81L);
        verify(refreshTokenRepository).revokeFamily(eq("device-session-family"), any());
        var lockOrder = inOrder(refreshTokenRepository, userRepository);
        lockOrder.verify(refreshTokenRepository).findUserIdByTokenHash(hash("refresh-token"));
        lockOrder.verify(userRepository).findByIdForUpdate(81L);
        lockOrder.verify(refreshTokenRepository).findByTokenHashForUpdate(hash("refresh-token"));
    }

    @Test
    void shouldNotResolveOwnerOrRevokeFamilyForUnknownRefreshToken() throws Exception {
        when(refreshTokenRepository.findUserIdByTokenHash(hash("unknown-token")))
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
        when(refreshTokenRepository.findUserIdByTokenHash(hash("expired-token")))
                .thenReturn(Optional.of(82L));
        when(userRepository.findByIdForUpdate(82L)).thenReturn(Optional.of(expired.getUser()));
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
        when(refreshTokenRepository.findUserIdByTokenHash(hash("revoked-token")))
                .thenReturn(Optional.of(83L));
        when(userRepository.findByIdForUpdate(83L)).thenReturn(Optional.of(revoked.getUser()));
        when(refreshTokenRepository.findByTokenHashForUpdate(hash("revoked-token")))
                .thenReturn(Optional.of(revoked));

        assertThat(service.revokeAndGetUserId("revoked-token")).isEmpty();

        verify(refreshTokenRepository).revokeFamily(eq("revoked-family"), any());
    }

    @Test
    void shouldLockTheUserBeforeRevokingEveryFamily() {
        when(userRepository.findByIdForUpdate(90L)).thenReturn(Optional.of(user(90L)));

        service.revokeAllForUser(90L);

        var lockOrder = inOrder(userRepository, refreshTokenRepository);
        lockOrder.verify(userRepository).findByIdForUpdate(90L);
        lockOrder.verify(refreshTokenRepository).revokeAllForUser(eq(90L), any());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setState(UserState.ENABLED);
        return user;
    }

    private String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
