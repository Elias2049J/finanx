package com.elias.mismonedas.identity.repository;

import com.elias.mismonedas.identity.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.messaging.rabbitmq.consumer-auto-startup=false")
@Transactional
class UserEmailNormalizationTest {

    @Autowired private UserRepository userRepository;

    @Test
    void shouldRejectEmailsThatDifferOnlyByCase() {
        String suffix = UUID.randomUUID().toString();
        String mixedCaseEmail = "Case-" + suffix + "@example.test";

        userRepository.saveAndFlush(user(mixedCaseEmail));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user(mixedCaseEmail.toLowerCase())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("test-password");
        return user;
    }
}
