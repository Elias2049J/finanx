package com.elias.mismonedas.notifications.controller;

import com.elias.mismonedas.notifications.service.impl.NotificationDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(NotificationReplayControllerSecurityTest.TestConfig.class)
class NotificationReplayControllerSecurityTest {

    @Autowired
    private NotificationReplayController controller;

    @Autowired
    private NotificationDeliveryService deliveryService;

    @BeforeEach
    void clearPreviousTestInteractions() {
        clearInvocations(deliveryService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void userRoleCannotReplayFailedNotifications() {
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_USER"));

        assertThatThrownBy(() -> controller.replay(91L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(roles = "SYSTEM")
    void systemRoleCanRequestReplay() {
        controller.replay(91L);

        verify(deliveryService).replayFailed(91L);
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        NotificationDeliveryService notificationDeliveryService() {
            return mock(NotificationDeliveryService.class);
        }

        @Bean
        NotificationReplayController notificationReplayController(
                NotificationDeliveryService deliveryService
        ) {
            return new NotificationReplayController(deliveryService);
        }
    }

}
