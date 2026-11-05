package com.elias.mismonedas.identity.flow;

import com.elias.mismonedas.identity.domain.entity.EmailVerificationChallenge;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.AuthProvider;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.dto.auth.EmailVerificationResendRequest;
import com.elias.mismonedas.identity.dto.auth.LoginRequest;
import com.elias.mismonedas.identity.dto.auth.PasswordResetConfirmRequest;
import com.elias.mismonedas.identity.dto.auth.PasswordResetRequest;
import com.elias.mismonedas.identity.dto.auth.RegisterVerificationRequest;
import com.elias.mismonedas.identity.repository.EmailVerificationChallengeRepository;
import com.elias.mismonedas.identity.repository.UserPreferencesRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.service.AuthTokenService;
import com.elias.mismonedas.identity.service.EmailVerificationEmailSender;
import com.elias.mismonedas.shared.exception.InvalidRefreshTokenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.SecureRandom;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.messaging.rabbitmq.consumer-auto-startup=false",
        "app.email-verification.global-send-limit-per-minute=1000",
        "app.email-verification.hmac-secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
@Import(AuthEmailVerificationFlowIntegrationTest.TestConfig.class)
class AuthEmailVerificationFlowIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public SecureRandom secureRandom() {
            return new SecureRandom() {
                @Override
                public int nextInt(int bound) {
                    return 0;
                }
            };
        }

        @Bean
        @Primary
        public EmailVerificationEmailSender emailVerificationEmailSender() {
            EmailVerificationEmailSender mock = Mockito.mock(EmailVerificationEmailSender.class);
            doNothing().when(mock).sendVerificationCode(Mockito.anyString(), Mockito.anyString(), Mockito.any());
            doNothing().when(mock).sendPasswordResetCode(Mockito.anyString(), Mockito.anyString(), Mockito.any());
            return mock;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPreferencesRepository preferencesRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private EmailVerificationChallengeRepository challengeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String email;

    @AfterEach
    void cleanup() {
        if (email != null) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                challengeRepository.deleteByEmailAndPurpose(email, EmailVerificationPurpose.REGISTRATION);
                challengeRepository.deleteByEmailAndPurpose(email, EmailVerificationPurpose.EXISTING_ACCOUNT);
                challengeRepository.deleteByEmailAndPurpose(email, EmailVerificationPurpose.PASSWORD_RESET);
                userRepository.findByEmail(email).ifPresent(user -> {
                    preferencesRepository.deleteByUser_Id(user.getId());
                    eventPublisher.publishEvent(new UserDeletedEvent(user.getId()));
                    userRepository.delete(user);
                });
            });
        }
    }

    @Test
    void registerFlowIssuesOtpAndVerifiesToCreateUser() throws Exception {
        email = "flow-" + UUID.randomUUID() + "@example.test";
        RegisterVerificationRequest start = new RegisterVerificationRequest();
        start.setName("Flow");
        start.setLastname("Test");
        start.setEmail(email);
        start.setPassword("Passw0rd!");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(start)))
                .andExpect(status().isAccepted());

        EmailVerificationChallenge challenge = challengeRepository.findByEmailAndPurposeForUpdate(
                        email, EmailVerificationPurpose.REGISTRATION)
                .orElseThrow();
        String code = "000000";

        RegisterVerificationRequest verify = new RegisterVerificationRequest();
        verify.setName("Flow");
        verify.setLastname("Test");
        verify.setEmail(email);
        verify.setPassword("Passw0rd!");
        verify.setCode(code);

        mockMvc.perform(post("/api/v1/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verify)))
                .andExpect(status().isNoContent());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getPassword()).isNotBlank();
    }

    @Test
    void existingUnverifiedLocalAccountCanResendAndConfirm() throws Exception {
        email = "existing-" + UUID.randomUUID() + "@example.test";
        User user = new User();
        user.setName("Ex");
        user.setLastname("Ist");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Passw0rd!"));
        user.setEmailVerified(false);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(com.elias.mismonedas.identity.domain.enums.Role.USER);
        userRepository.saveAndFlush(user);

        EmailVerificationResendRequest resend = new EmailVerificationResendRequest();
        resend.setEmail(email);

        mockMvc.perform(post("/api/v1/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resend)))
                .andExpect(status().isAccepted());

        EmailVerificationChallenge challenge = challengeRepository.findByEmailAndPurposeForUpdate(
                        email, EmailVerificationPurpose.EXISTING_ACCOUNT)
                .orElseThrow();
        String code = "000000";

        EmailVerificationResendRequest confirm = new EmailVerificationResendRequest();
        confirm.setEmail(email);
        confirm.setCode(code);

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isNoContent());

        User verified = userRepository.findByEmail(email).orElseThrow();
        assertThat(verified.isEmailVerified()).isTrue();
    }

    @Test
    void loginWithUnverifiedLocalAccountIsRejectedWithEmailNotVerified() throws Exception {
        email = "unverified-" + UUID.randomUUID() + "@example.test";
        User user = new User();
        user.setName("Un");
        user.setLastname("Ver");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Passw0rd!"));
        user.setEmailVerified(false);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(com.elias.mismonedas.identity.domain.enums.Role.USER);
        userRepository.saveAndFlush(user);

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("Passw0rd!");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.path("code").asText()).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void passwordResetChangesPasswordAndRevokesExistingSessions() throws Exception {
        email = "reset-" + UUID.randomUUID() + "@example.test";
        User user = new User();
        user.setName("Re");
        user.setLastname("Set");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("OldPassw0rd!"));
        user.setEmailVerified(true);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(com.elias.mismonedas.identity.domain.enums.Role.USER);
        userRepository.saveAndFlush(user);

        String refreshToken = authTokenService.issue(user).getRefreshToken();

        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail(email);

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        assertThat(challengeRepository.findByEmailAndPurposeForUpdate(
                email, EmailVerificationPurpose.PASSWORD_RESET)).isPresent();

        PasswordResetConfirmRequest confirm = new PasswordResetConfirmRequest();
        confirm.setEmail(email);
        confirm.setCode("000000");
        confirm.setNewPassword("NewPassw0rd!");

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isNoContent());

        User updated = userRepository.findByEmail(email).orElseThrow();
        assertThat(passwordEncoder.matches("NewPassw0rd!", updated.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("OldPassw0rd!", updated.getPassword())).isFalse();
        assertThatThrownBy(() -> authTokenService.refresh(refreshToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void passwordResetWithWrongCodeKeepsPasswordAndReportsStatus() throws Exception {
        email = "bad-reset-" + UUID.randomUUID() + "@example.test";
        User user = new User();
        user.setName("Bad");
        user.setLastname("Reset");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("OldPassw0rd!"));
        user.setEmailVerified(true);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(com.elias.mismonedas.identity.domain.enums.Role.USER);
        userRepository.saveAndFlush(user);

        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail(email);

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        PasswordResetConfirmRequest confirm = new PasswordResetConfirmRequest();
        confirm.setEmail(email);
        confirm.setCode("999999");
        confirm.setNewPassword("NewPassw0rd!");

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Password-Reset-Status", "INVALID_CODE"));

        User unchanged = userRepository.findByEmail(email).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassw0rd!", unchanged.getPassword())).isTrue();
    }

    // Code fixed to 000000 via mocked SecureRandom and known HMAC context in tests.
}
