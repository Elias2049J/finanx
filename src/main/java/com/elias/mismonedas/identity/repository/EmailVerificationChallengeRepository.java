package com.elias.mismonedas.identity.repository;

import com.elias.mismonedas.identity.domain.entity.EmailVerificationChallenge;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface EmailVerificationChallengeRepository extends JpaRepository<EmailVerificationChallenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select challenge from EmailVerificationChallenge challenge
            where challenge.email = :email and challenge.purpose = :purpose
            """)
    Optional<EmailVerificationChallenge> findByEmailAndPurposeForUpdate(
            @Param("email") String email,
            @Param("purpose") EmailVerificationPurpose purpose);

    @Modifying
    @Query("delete from EmailVerificationChallenge challenge where challenge.expiresAt <= :now")
    int deleteExpiredAtOrBefore(@Param("now") OffsetDateTime now);
}
