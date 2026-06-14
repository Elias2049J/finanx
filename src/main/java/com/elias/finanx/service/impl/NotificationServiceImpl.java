package com.elias.finanx.service.impl;

import com.elias.finanx.dto.notification.NotificationDTO;
import com.elias.finanx.entity.Notification;
import com.elias.finanx.entity.User;
import com.elias.finanx.entity.enums.NotificationState;
import com.elias.finanx.mapper.NotificationMapper;
import com.elias.finanx.outbox.NotificationOutbox;
import com.elias.finanx.outbox.OutboxState;
import com.elias.finanx.outbox.persistence.NotificationOutboxRepository;
import com.elias.finanx.repository.NotificationRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final UserRepository userRepository;

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generate(Consumer<Notification.NotificationBuilder> builderConsumer) {
        Notification.NotificationBuilder builder = Notification.builder();
        builderConsumer.accept(builder);

        Notification saved = notificationRepository.save(builder.build());
        NotificationDTO dto = notificationMapper.toResponse(saved);

        outboxRepository.save(NotificationOutbox.builder()
                .notificationId(saved.getId())
                .type(saved.getType())
                .payload(toJson(dto))
                .state(OutboxState.PENDING)
                .createdAt(OffsetDateTime.now())
                .build());

    }

    private String toJson(NotificationDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar NotificationDTO a JSON para outbox", e);
        }
    }

    @Override
    public NotificationDTO findById(Long id) {
        return notificationMapper.toResponse(notificationRepository.findById(id).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> findAllByUser(Long userId) {
        return notificationRepository.findAllByUser_Id(userId)
                .stream().map(notificationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> findAllByUserAndState(Long userId, NotificationState state) {
        return notificationRepository.findAllByUser_IdAndState(userId, state)
                .stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    @Override
    public void markAsRead(Long id) {
        Notification n = notificationRepository.findById(id).orElseThrow();
        n.setState(NotificationState.READ);
        notificationRepository.save(n);
    }

    @Override
    public void discard(Long id) {
        Notification n = notificationRepository.findById(id).orElseThrow();
        n.setState(NotificationState.DISCARD);
        notificationRepository.save(n);
    }

    @Transactional
    @Override
    public void disable(Long id) {
        Notification existing = notificationRepository.findById(id).orElseThrow();
        ZoneId zoneId = existing.getUser().getTimeZone().toZoneId();
        existing.setDisabledAt(OffsetDateTime.now(zoneId));
        notificationRepository.save(existing);
    }

    @Override
    public void purgeOlderThan(LocalDateTime threshold, long userId) {
        User u = userRepository.findById(userId).orElseThrow();
        List<Notification> nList = notificationRepository.findAllByUser_IdAndStateAndSentAtBefore(userId, NotificationState.SENT, OffsetDateTime.now(u.getTimeZone().toZoneId()));
        nList.forEach(n -> n.setState(NotificationState.DISCARD));
        notificationRepository.saveAll(nList);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> findAllScheduledByUser(long userId) {
        return notificationRepository.findAllByUser_IdAndStateAndScheduledAtBefore(userId, NotificationState.SCHEDULED, OffsetDateTime.now())
                .stream()
                .map(notificationMapper::toResponse)
                .toList();
    }
}
