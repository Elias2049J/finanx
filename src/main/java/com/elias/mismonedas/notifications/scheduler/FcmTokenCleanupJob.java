package com.elias.mismonedas.notifications.scheduler;

import com.elias.mismonedas.notifications.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmTokenCleanupJob {

    private final FcmTokenRepository fcmTokenRepository;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void purgeStaleTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(60);
        int deleted = fcmTokenRepository.deleteByUpdatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Eliminados {} tokens FCM stale (anteriores a {})", deleted, cutoff);
        }
    }
}
