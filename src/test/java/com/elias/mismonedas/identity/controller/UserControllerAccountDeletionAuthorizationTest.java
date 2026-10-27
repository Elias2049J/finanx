package com.elias.mismonedas.identity.controller;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.identity.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UserControllerAccountDeletionAuthorizationTest.TestConfig.class)
class UserControllerAccountDeletionAuthorizationTest {

    @Autowired
    private UserController userController;

    @Autowired
    private UserService userService;

    @Autowired
    private SecurityService securityService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        reset(userService, securityService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userCannotRequestDeletionForAnotherUser() {
        authenticateAsUser();
        when(securityService.getCurrentUser()).thenReturn(user(10L));

        assertThatThrownBy(() -> userController.requestDeletion(20L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userService);
    }

    @Test
    void userCannotCancelDeletionForAnotherUser() {
        authenticateAsUser();
        when(securityService.getCurrentUser()).thenReturn(user(10L));

        assertThatThrownBy(() -> userController.cancelDeletion(20L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userService);
    }

    @Test
    void userCanRequestAndCancelDeletionForSelf() {
        authenticateAsUser();
        when(securityService.getCurrentUser()).thenReturn(user(10L));

        userController.requestDeletion(10L);
        userController.cancelDeletion(10L);

        verify(userService).requestAccountDeletion(10L);
        verify(userService).cancelAccountDeletion(10L);
    }

    @Test
    void systemCanRequestAndCancelDeletionForAnotherUser() {
        authenticateAsSystem();

        userController.requestDeletion(20L);
        userController.cancelDeletion(20L);

        verify(userService).requestAccountDeletion(20L);
        verify(userService).cancelAccountDeletion(20L);
        verifyNoInteractions(securityService);
    }

    @Test
    void otherAuthenticatedRolesCannotUseAccountDeletionEndpoints() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "admin", "credentials", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThatThrownBy(() -> userController.requestDeletion(20L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userController.cancelDeletion(20L))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(userService, securityService);
    }

    private void authenticateAsUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "10", "credentials", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private void authenticateAsSystem() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "system", "credentials", List.of(new SimpleGrantedAuthority("ROLE_SYSTEM"))));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        SecurityService securityService() {
            return mock(SecurityService.class);
        }

        @Bean
        UserController userController(UserService userService, SecurityService securityService) {
            return new UserController(userService, securityService);
        }
    }
}
