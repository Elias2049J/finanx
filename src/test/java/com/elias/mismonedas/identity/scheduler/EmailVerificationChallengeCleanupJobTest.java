package com.elias.mismonedas.identity.scheduler;

import com.elias.mismonedas.identity.repository.EmailVerificationChallengeRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class EmailVerificationChallengeCleanupJobTest {

    @Test
    void shouldPurgeChallengesExpiredAtTheCurrentTime() {
        EmailVerificationChallengeRepository repository = mock(EmailVerificationChallengeRepository.class);
        EmailVerificationChallengeCleanupJob job = new EmailVerificationChallengeCleanupJob(repository);

        job.purgeExpiredChallenges();

        verify(repository).deleteExpiredAtOrBefore(any());
    }
}
