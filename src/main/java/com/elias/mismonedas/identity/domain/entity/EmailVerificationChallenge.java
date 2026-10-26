package com.elias.mismonedas.identity.domain.entity;

import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.domain.EmailVerificationPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "email_verification_challenges",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_email_verification_email_purpose",
                columnNames = {"email", "purpose"}),
        indexes = @Index(name = "idx_email_verification_expires", columnList = "expires_at")
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private EmailVerificationPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Builder.Default
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "last_sent_at", nullable = false)
    private OffsetDateTime lastSentAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public boolean isExpiredAt(OffsetDateTime now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public void recordFailedAttempt() {
        if (failedAttempts < EmailVerificationPolicy.MAX_FAILED_ATTEMPTS) {
            failedAttempts++;
        }
    }

    public boolean hasReachedAttemptLimit() {
        return failedAttempts >= EmailVerificationPolicy.MAX_FAILED_ATTEMPTS;
    }

    public void replaceCode(String newCodeHash, OffsetDateTime sentAt, OffsetDateTime newExpiresAt) {
        codeHash = newCodeHash;
        lastSentAt = sentAt;
        expiresAt = newExpiresAt;
        failedAttempts = 0;
    }
}
