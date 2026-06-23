package com.elias.finanx.adapter.auth;

import com.elias.finanx.dto.auth.GoogleIdTokenPayload;
import com.elias.finanx.dto.auth.GoogleLoginRequest;
import com.elias.finanx.dto.auth.LoginResponse;
import com.elias.finanx.dto.user.UserRequest;
import com.elias.finanx.dto.user.UserResponse;
import com.elias.finanx.entity.User;
import com.elias.finanx.entity.enums.Role;
import com.elias.finanx.entity.enums.TimeZone;
import com.elias.finanx.mapper.UserMapper;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.JwtTokenService;
import com.elias.finanx.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Override
    @Transactional
    public LoginResponse authenticate(GoogleLoginRequest request) {
        var payload = googleIdTokenVerifier.verify(request.getIdToken(), googleClientId);
        String email = payload.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            return login(payload);
        }
        return register(payload);
    }

    @Override
    public LoginResponse login(GoogleIdTokenPayload payload) {
        String email = payload.email();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User is not registered with Google"));

        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(payload.givenName());
        }
        if (user.getLastname() == null || user.getLastname().isBlank()) {
            user.setLastname(payload.familyName());
        }
        if (user.getTimeZone() == null) {
            user.setTimeZone(TimeZone.AMERICA_LIMA);
        }
        if (user.getRole() == null) {
            user.setRole(Role.USER);
        }
        if (user.getMoneyBalance() == null) {
            user.setMoneyBalance(BigDecimal.ZERO);
        }

        String token = jwtTokenService.generateToken(user);
        UserResponse userDto = userMapper.toResponse(user);
        return new LoginResponse(userDto, token);
    }

    @Override
    public LoginResponse register(GoogleIdTokenPayload payload) {
        String email = payload.email().trim().toLowerCase();

        User existing = userRepository.findByEmail(email).orElse(null);

        if (existing != null) {
            existing.setName(payload.givenName());
            existing.setLastname(payload.familyName());
            existing.setUsername(email);

            User saved = userRepository.save(existing);

            String token = jwtTokenService.generateToken(saved);
            return new LoginResponse(userMapper.toResponse(saved), token);
        }

        UserRequest request = new UserRequest();
        request.setEmail(email);
        request.setUsername(email);
        request.setName(payload.givenName());
        request.setLastname(payload.familyName());
        request.setPassword(randomSecret());
        request.setTimeZone(TimeZone.AMERICA_LIMA);

        return userService.register(request);
    }


    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
