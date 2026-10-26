package com.elias.mismonedas.identity.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EmailVerificationChallengeLock {

    private final JdbcTemplate jdbcTemplate;

    public void setLocalLockTimeout() {
        jdbcTemplate.execute("SET LOCAL lock_timeout = '5s'");
    }

    public void acquire(String key) {
        jdbcTemplate.execute(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (PreparedStatementCallback<Void>) statement -> {
                    statement.setString(1, key);
                    statement.execute();
                    return null;
                });
    }
}
