package com.elias.finanx.service.impl.scheduler;

import com.elias.finanx.port.NotificationPort;
import com.elias.finanx.dto.notification.NotificationDTO;
import com.elias.finanx.entity.enums.NotificationState;
import com.elias.finanx.outbox.NotificationOutbox;
import com.elias.finanx.outbox.OutboxState;
import com.elias.finanx.outbox.persistence.NotificationOutboxRepository;
import com.elias.finanx.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPort notificationPort;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processOutbox() {
        List<NotificationOutbox> pending = outboxRepository.findAllByState(OutboxState.PENDING);

        for (NotificationOutbox entry : pending) {
            try {
                NotificationDTO dto = objectMapper.readValue(entry.getPayload(), NotificationDTO.class);
                notificationPort.send(dto);

                entry.setState(OutboxState.SENT);
                entry.setSentAt(OffsetDateTime.now());

                if (entry.getNotificationId() != null) {
                    notificationRepository.findById(entry.getNotificationId()).ifPresentOrElse(n -> {
                        n.setState(NotificationState.SENT);
                        n.setSentAt(entry.getSentAt());
                        notificationRepository.save(n);
                    }, () -> log.warn("Outbox id={} apunta a notificationId={} inexistente", entry.getId(), entry.getNotificationId()));
                } else {
                    log.warn("Outbox id={} no tiene notificationId; no se actualiza entidad Notification", entry.getId());
                }

            } catch (Exception e) {
                entry.setState(OutboxState.FAILED);
                log.error("Error enviando outbox id={} notificationId={}", entry.getId(), entry.getNotificationId(), e);
            }

            outboxRepository.save(entry);
        }
    }
}
