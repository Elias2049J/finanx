package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class EmailVerificationCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DOMAIN = "finanx:email-verification:v1";
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKeySpec secretKey;

    public EmailVerificationCodeHasher(
            @Value("${app.email-verification.hmac-secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("Email verification HMAC secret must contain at least 32 UTF-8 bytes");
        }
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String email, EmailVerificationPurpose purpose, String code) {
        return HexFormat.of().formatHex(mac(payload(email, purpose, code)));
    }

    public boolean matches(
            String email,
            EmailVerificationPurpose purpose,
            String code,
            String storedHash) {
        if (storedHash == null || !storedHash.matches("[0-9a-f]{64}")) {
            return false;
        }
        byte[] expected = mac(payload(email, purpose, code));
        byte[] actual = HexFormat.of().parseHex(storedHash);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] payload(String email, EmailVerificationPurpose purpose, String code) {
        return (DOMAIN + "\u0000" + email + "\u0000" + purpose.name() + "\u0000" + code)
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] mac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }
}
