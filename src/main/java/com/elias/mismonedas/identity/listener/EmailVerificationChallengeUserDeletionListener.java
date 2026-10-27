package com.elias.mismonedas.identity.listener;

import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletionOrder;
import com.elias.mismonedas.identity.repository.EmailVerificationChallengeRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationChallengeUserDeletionListener {

    private final UserRepository userRepository;
    private final EmailVerificationChallengeRepository challengeRepository;

    @EventListener
    @Order(UserDeletionOrder.EMAIL_VERIFICATION)
    public void onUserDeleted(UserDeletedEvent event) {
        userRepository.findById(event.userId()).ifPresent(user -> {
            String email = EmailNormalizer.normalize(user.getEmail());
            challengeRepository.deleteByEmailAndPurpose(email, EmailVerificationPurpose.REGISTRATION);
            challengeRepository.deleteByEmailAndPurpose(email, EmailVerificationPurpose.EXISTING_ACCOUNT);
        });
    }
}
