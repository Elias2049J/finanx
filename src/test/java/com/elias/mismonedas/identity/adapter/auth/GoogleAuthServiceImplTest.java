package com.elias.mismonedas.identity.adapter.auth;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.AuthProvider;
import com.elias.mismonedas.identity.domain.enums.Role;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.dto.auth.GoogleIdTokenPayload;
import com.elias.mismonedas.identity.dto.auth.GoogleLoginRequest;
import com.elias.mismonedas.identity.dto.user.UserRequest;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.service.AuthTokenService;
import com.elias.mismonedas.identity.service.UserService;
import com.elias.mismonedas.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthTokenService authTokenService;
    @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;

    private GoogleAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GoogleAuthServiceImpl(
                userRepository, userService, passwordEncoder, authTokenService, googleIdTokenVerifier);
    }

    @Test
    void shouldLoginByProviderSubjectWithoutTouchingPassword() {
        User user = user(1L, "ana@gmail.com", "random-hash");
        user.setName("Ana");
        user.setLastname("Perez");
        AuthResponse response = new AuthResponse("access", "refresh", 3600L);

        when(googleIdTokenVerifier.verify("id-token")).thenReturn(payload("google-1", "ana@gmail.com"));
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "google-1"))
                .thenReturn(Optional.of(user));
        when(authTokenService.issue(user)).thenReturn(response);

        AuthResponse result = service.authenticate(request("id-token"));

        assertThat(result).isSameAs(response);
        assertThat(user.getPassword()).isEqualTo("random-hash");
        verify(userRepository, never()).save(any());
        verify(authTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void shouldRejectAutoLinkWhenEmailBelongsToLocalAccount() {
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(payload("google-1", "victim@gmail.com"));
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "google-1"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("victim@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate(request("id-token")))
                .isInstanceOf(ConflictException.class);

        verify(userService, never()).register(any());
        verify(authTokenService, never()).issue(any());
    }

    @Test
    void shouldRegisterNewGoogleAccountAndMarkItVerified() {
        AuthResponse response = new AuthResponse("access", "refresh", 3600L);
        User created = user(10L, "nueva@gmail.com", "random");

        when(googleIdTokenVerifier.verify("id-token")).thenReturn(payload("google-9", "nueva@gmail.com"));
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "google-9"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("nueva@gmail.com")).thenReturn(false);
        when(userService.register(any(UserRequest.class))).thenReturn(response);
        when(userRepository.findByEmail("nueva@gmail.com")).thenReturn(Optional.of(created));

        AuthResponse result = service.authenticate(request("id-token"));

        ArgumentCaptor<UserRequest> captor = ArgumentCaptor.forClass(UserRequest.class);
        verify(userService).register(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("nueva@gmail.com");
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(created.getProviderUserId()).isEqualTo("google-9");
        assertThat(created.isEmailVerified()).isTrue();
        verify(userRepository).save(created);
        assertThat(result).isSameAs(response);
    }

    @Test
    void shouldNormalizeEmailBeforeLookupAndRegistration() {
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(
                payload("google-2", "  Nueva@Gmail.COM "));
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "google-2"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("nueva@gmail.com")).thenReturn(false);
        when(userService.register(any(UserRequest.class))).thenReturn(new AuthResponse("a", "r", 3600L));
        when(userRepository.findByEmail("nueva@gmail.com")).thenReturn(Optional.empty());

        service.authenticate(request("id-token"));

        ArgumentCaptor<UserRequest> captor = ArgumentCaptor.forClass(UserRequest.class);
        verify(userService).register(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("nueva@gmail.com");
    }

    @Test
    void shouldOnlyFillMissingProfileFields() {
        User user = user(1L, "ana@gmail.com", "random-hash");
        user.setName("Ana");

        when(googleIdTokenVerifier.verify("id-token")).thenReturn(
                new GoogleIdTokenPayload("google-1", "ana@gmail.com", "Otro", "Perez"));
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, "google-1"))
                .thenReturn(Optional.of(user));
        when(authTokenService.issue(user)).thenReturn(new AuthResponse("a", "r", 3600L));

        service.authenticate(request("id-token"));

        assertThat(user.getName()).isEqualTo("Ana");
        assertThat(user.getLastname()).isEqualTo("Perez");
        verify(userRepository).save(user);
    }

    private static GoogleLoginRequest request(String idToken) {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setIdToken(idToken);
        return request;
    }

    private static GoogleIdTokenPayload payload(String subject, String email) {
        return new GoogleIdTokenPayload(subject, email, "Ana", "Perez");
    }

    private static User user(Long id, String email, String password) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPassword(password);
        user.setRole(Role.USER);
        return user;
    }
}
