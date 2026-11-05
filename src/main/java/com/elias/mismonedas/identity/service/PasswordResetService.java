package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.AuthProvider;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.util.EmailAddressValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restablecimiento de contrasena mediante un codigo de un solo uso enviado al
 * correo. Solo aplica a cuentas locales verificadas; las cuentas de Google no
 * tienen contrasena gestionada por Finanx.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;

    /**
     * La respuesta al cliente no revela si el correo existe. Por eso cualquier
     * fallo de envio, cooldown o limite global se registra y se ignora.
     */
    public void requestReset(String rawEmail) {
        String email = EmailAddressValidator.normalize(rawEmail);
        userRepository.findByEmail(email)
                .filter(this::canResetPassword)
                .ifPresent(user -> {
                    try {
                        emailVerificationService.issueChallenge(
                                email, EmailVerificationPurpose.PASSWORD_RESET);
                    } catch (RuntimeException failure) {
                        log.warn("No se pudo enviar el codigo de restablecimiento para el usuario {}: {}",
                                user.getId(), failure.getClass().getSimpleName());
                    }
                });
    }

    @Transactional
    public EmailVerificationChallengeManager.VerificationResult confirmReset(
            String rawEmail, String code, String newPassword) {
        String email = EmailAddressValidator.normalize(rawEmail);
        EmailVerificationChallengeManager.VerificationResult result =
                emailVerificationService.verifyChallenge(
                        email, EmailVerificationPurpose.PASSWORD_RESET, code);
        if (result != EmailVerificationChallengeManager.VerificationResult.VERIFIED) {
            return result;
        }

        User user = userRepository.findByEmailForUpdate(email).orElse(null);
        if (user == null || !canResetPassword(user)) {
            log.warn("Codigo de restablecimiento valido para una cuenta no elegible");
            return EmailVerificationChallengeManager.VerificationResult.INVALID_CODE;
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        authTokenService.revokeAllForUser(user.getId());

        log.info("Contrasena restablecida para el usuario {}", user.getId());
        return EmailVerificationChallengeManager.VerificationResult.VERIFIED;
    }

    private boolean canResetPassword(User user) {
        AuthProvider provider = user.getAuthProvider();
        return (provider == null || provider == AuthProvider.LOCAL)
                && Boolean.TRUE.equals(user.isEmailVerified())
                && !user.isPendingDeletion()
                && user.isEnabled();
    }
}
