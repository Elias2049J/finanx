package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.notifications.domain.entity.FcmToken;
import com.elias.mismonedas.notifications.dto.fcm.RegisterFcmTokenRequest;
import com.elias.mismonedas.notifications.repository.FcmTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmTokenServiceImplTest {

    @Mock private FcmTokenRepository fcmTokenRepository;
    @Mock private SecurityService securityService;

    private FcmTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FcmTokenServiceImpl(fcmTokenRepository, securityService);
    }

    @Test
    void shouldTransferExistingTokenToCurrentlyAuthenticatedUser() {
        User previousOwner = user(3L);
        User currentOwner = user(8L);
        FcmToken existing = FcmToken.builder()
                .id(21L)
                .user(previousOwner)
                .token("device-token")
                .deviceName("Old device name")
                .build();
        when(securityService.getCurrentUser()).thenReturn(currentOwner);
        doAnswer(invocation -> {
            existing.setUser(currentOwner);
            existing.setDeviceName(invocation.getArgument(2));
            return null;
        }).when(fcmTokenRepository).upsertByToken(eq(8L), eq("device-token"), eq("Pixel"), any());
        when(fcmTokenRepository.findByToken("device-token")).thenReturn(Optional.of(existing));

        var response = service.register(new RegisterFcmTokenRequest("device-token", "Pixel"));

        assertThat(response.getId()).isEqualTo(21L);
        assertThat(existing.getUser().getId()).isEqualTo(8L);
        assertThat(response.getDeviceName()).isEqualTo("Pixel");
        verify(fcmTokenRepository).upsertByToken(eq(8L), eq("device-token"), eq("Pixel"), any());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
