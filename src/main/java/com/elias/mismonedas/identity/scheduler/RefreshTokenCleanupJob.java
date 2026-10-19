package com.elias.mismonedas.identity.scheduler;

import com.elias.mismonedas.identity.repository.RefreshTokenRepository;
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
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 30 3 * * ?")
    @SchedulerLock(name = "refreshTokenCleanupJob.purgeExpiredTokens", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(OffsetDateTime.now());
        if (deleted > 0) {
            log.info("Eliminados {} refresh tokens expirados", deleted);
        }
    }
}
