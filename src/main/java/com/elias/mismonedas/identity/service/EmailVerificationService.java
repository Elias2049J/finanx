package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.EmailVerificationPolicy;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.util.EmailAddressValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationChallengeManager challengeManager;
    private final EmailVerificationCodeHasher codeHasher;
    private final EmailVerificationEmailSender emailSender;
    private final SecureRandom secureRandom;

    public void issueChallenge(String rawEmail, EmailVerificationPurpose purpose) {
        String email = EmailAddressValidator.normalize(rawEmail);
        String code = String.format(
                Locale.ROOT, "%06d", secureRandom.nextInt(EmailVerificationPolicy.CODE_UPPER_BOUND));
        String codeHash = codeHasher.hash(email, purpose, code);
        Long challengeId = challengeManager.persistChallenge(email, purpose, codeHash);

        try {
            emailSender.sendVerificationCode(email, code, EmailVerificationPolicy.CODE_TTL);
        } catch (RuntimeException deliveryFailure) {
            try {
                challengeManager.invalidateIfCurrent(challengeId, codeHash);
            } catch (RuntimeException invalidationFailure) {
                deliveryFailure.addSuppressed(invalidationFailure);
            }
            throw new EmailVerificationDeliveryException(deliveryFailure);
        }
    }

    public EmailVerificationChallengeManager.VerificationResult verifyChallenge(
            String rawEmail,
            EmailVerificationPurpose purpose,
            String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return EmailVerificationChallengeManager.VerificationResult.INVALID_CODE;
        }
        String email = EmailAddressValidator.normalize(rawEmail);
        return challengeManager.verifyChallenge(email, purpose, code);
    }
}
