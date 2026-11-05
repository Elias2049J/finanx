package com.elias.mismonedas.billing.controller;

import com.elias.mismonedas.billing.service.BillingService;
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

@SpringJUnitConfig(SubscriptionControllerSecurityTest.TestConfig.class)
class SubscriptionControllerSecurityTest {

    @Autowired
    private SubscriptionController controller;

    @Autowired
    private BillingService billingService;

    @BeforeEach
    void clearPreviousTestInteractions() {
        clearInvocations(billingService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void userRoleCannotActivateSubscription() {
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_USER"));

        assertThatThrownBy(() -> controller.activate(42L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(billingService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void userRoleCannotRenewSubscription() {
        assertThatThrownBy(() -> controller.renew(42L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(billingService);
    }

    @Test
    @WithMockUser(roles = "SYSTEM")
    void systemRoleCanActivateSubscription() {
        controller.activate(42L);

        verify(billingService).activate(42L);
    }

    @Test
    @WithMockUser(roles = "SYSTEM")
    void systemRoleCanRenewSubscription() {
        controller.renew(42L);

        verify(billingService).renew(42L);
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        BillingService billingService() {
            return mock(BillingService.class);
        }

        @Bean
        SubscriptionController subscriptionController(BillingService billingService) {
            return new SubscriptionController(billingService);
        }
    }
}
