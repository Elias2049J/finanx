package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.dto.user.UserRequest;
import com.elias.mismonedas.identity.dto.user.UserResponse;
import com.elias.mismonedas.identity.mapper.UserMapper;
import com.elias.mismonedas.identity.repository.UserPreferencesRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.identity.service.AuthTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthTokenService authTokenService;
    @Mock private UserPreferencesRepository preferencesRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(
                userRepository, userMapper, passwordEncoder, authTokenService,
                preferencesRepository, securityService, eventPublisher);
    }

    @Test
    void shouldCheckAndStoreNormalizedEmailDuringRegistration() {
        UserRequest request = request("  Alice@Example.test ");
        User user = new User();
        user.setEmail(request.getEmail());
        AuthResponse response = new AuthResponse("access", "refresh", 3600L);

        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.save(user)).thenReturn(user);
        when(authTokenService.issue(user)).thenReturn(response);

        assertThat(service.register(request)).isSameAs(response);

        verify(userRepository).existsByEmail("alice@example.test");
        assertThat(user.getEmail()).isEqualTo("alice@example.test");
    }

    @Test
    void shouldRejectCaseVariantOfExistingEmail() {
        UserRequest request = request("ALICE@example.test");
        User user = new User();
        when(userMapper.toEntity(request)).thenReturn(user);
        when(userRepository.existsByEmail(anyString()))
                .thenAnswer(invocation -> "alice@example.test".equals(invocation.getArgument(0)));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already taken");

        verify(userRepository).existsByEmail("alice@example.test");
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(authTokenService, eventPublisher);
    }

    @Test
    void shouldNormalizeEmailWhenUpdatingUser() {
        User existing = new User();
        existing.setId(7L);
        existing.setEmail("old@example.test");
        UserRequest request = request(" New@Example.test ");
        when(userRepository.findById(7L)).thenReturn(Optional.of(existing));
        doAnswer(invocation -> {
            existing.setEmail(request.getEmail());
            return null;
        }).when(userMapper).updateFromDto(request, existing);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.save(existing)).thenReturn(existing);
        when(userMapper.toResponse(existing)).thenReturn(mock(UserResponse.class));

        service.update(7L, request);

        assertThat(existing.getEmail()).isEqualTo("new@example.test");
    }

    private UserRequest request(String email) {
        UserRequest request = new UserRequest();
        request.setName("Test");
        request.setLastname("User");
        request.setEmail(email);
        request.setPassword("password");
        return request;
    }
}
