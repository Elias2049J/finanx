package com.elias.mismonedas.identity.flow;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.AuthProvider;
import com.elias.mismonedas.identity.domain.enums.Role;
import com.elias.mismonedas.identity.domain.enums.UserState;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.messaging.rabbitmq.consumer-auto-startup=false"
})
class AccountDeletionCancelAccessIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void canCancelPendingDeletionDuringGracePeriod() {
        String email = "del-" + UUID.randomUUID() + "@example.test";
        User user = new User();
        user.setName("Del");
        user.setLastname("Test");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Passw0rd!"));
        user.setEmailVerified(true);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(Role.USER);
        user.setState(UserState.ENABLED);
        user = userRepository.saveAndFlush(user);

        userService.requestAccountDeletion(user.getId());
        User pending = userRepository.findById(user.getId()).orElseThrow();
        assertThat(pending.isPendingDeletion()).isTrue();
        assertThat(pending.getState()).isEqualTo(UserState.DISABLED);

        userService.cancelAccountDeletion(user.getId());
        User restored = userRepository.findById(user.getId()).orElseThrow();
        assertThat(restored.isPendingDeletion()).isFalse();
        assertThat(restored.getState()).isEqualTo(UserState.ENABLED);
    }
}
