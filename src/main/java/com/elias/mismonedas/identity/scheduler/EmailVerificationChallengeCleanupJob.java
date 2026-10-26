package com.elias.mismonedas.identity.scheduler;

import com.elias.mismonedas.identity.repository.EmailVerificationChallengeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationChallengeCleanupJob {

    private final EmailVerificationChallengeRepository challengeRepository;

    @Scheduled(fixedDelay = 300_000)
    @SchedulerLock(
            name = "emailVerificationChallengeCleanupJob.purgeExpiredChallenges",
            lockAtMostFor = "PT2M",
            lockAtLeastFor = "PT30S")
    @Transactional
    public void purgeExpiredChallenges() {
        int deleted = challengeRepository.deleteExpiredAtOrBefore(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("Eliminados {} desafíos de verificación de email vencidos", deleted);
        }
    }
}
