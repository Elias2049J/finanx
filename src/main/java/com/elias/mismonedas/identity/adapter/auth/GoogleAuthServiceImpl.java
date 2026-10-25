package com.elias.mismonedas.identity.adapter.auth;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.AuthProvider;
import com.elias.mismonedas.identity.domain.enums.Role;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.dto.auth.GoogleIdTokenPayload;
import com.elias.mismonedas.identity.dto.auth.GoogleLoginRequest;
import com.elias.mismonedas.identity.dto.user.UserRequest;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.service.AuthTokenService;
import com.elias.mismonedas.identity.service.UserService;
import com.elias.mismonedas.identity.util.EmailNormalizer;
import com.elias.mismonedas.shared.exception.ConflictException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    @Override
    @Transactional
    public AuthResponse authenticate(GoogleLoginRequest request) {
        return login(googleIdTokenVerifier.verify(request.getIdToken()));
    }

    /**
     * Identifica la cuenta por el {@code sub} de Google, nunca por email.
     * <p>
     * Si el {@code sub} no esta registrado y el email ya pertenece a una cuenta local,
     * se rechaza: vincularla automaticamente permitiria que quien registro ese email
     * con una contrasena propia conservara el acceso (pre-hijack). El usuario debe
     * entrar con su metodo actual y vincular Google de forma explicita.
     */
    @Override
    @Transactional
    public AuthResponse login(GoogleIdTokenPayload payload) {
        return userRepository
                .findByAuthProviderAndProviderUserId(AuthProvider.GOOGLE, payload.subject())
                .map(user -> refreshProfileAndIssue(user, payload))
                .orElseGet(() -> registerOrReject(payload));
    }

    @Override
    @Transactional
    public AuthResponse register(GoogleIdTokenPayload payload) {
        return login(payload);
    }

    private AuthResponse registerOrReject(GoogleIdTokenPayload payload) {
        String email = EmailNormalizer.normalize(payload.email());
        if (userRepository.existsByEmail(email)) {
            log.warn("Rejected Google sign-in: email already belongs to a local account");
            throw new ConflictException(
                    "This email is already registered. Sign in with your email and password.");
        }
        return registerNewAccount(payload, email);
    }

    private AuthResponse registerNewAccount(GoogleIdTokenPayload payload, String email) {
        UserRequest request = new UserRequest();
        request.setEmail(email);
        request.setName(payload.givenName());
        request.setLastname(payload.familyName());
        request.setPassword(randomSecret());

        // userService.register publica UserRegisteredEvent (suscripcion inicial incluida).
        AuthResponse response = userService.register(request);

        userRepository.findByEmail(email).ifPresent(user -> {
            user.setAuthProvider(AuthProvider.GOOGLE);
            user.setProviderUserId(payload.subject());
            user.setEmailVerified(true);
            userRepository.save(user);
        });

        log.info("Google account registered");
        return response;
    }

    private AuthResponse refreshProfileAndIssue(User user, GoogleIdTokenPayload payload) {
        boolean changed = false;
        if (isBlank(user.getName()) && !isBlank(payload.givenName())) {
            user.setName(payload.givenName());
            changed = true;
        }
        if (isBlank(user.getLastname()) && !isBlank(payload.familyName())) {
            user.setLastname(payload.familyName());
            changed = true;
        }
        if (user.getRole() == null) {
            user.setRole(Role.USER);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }
        return authTokenService.issue(user);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
