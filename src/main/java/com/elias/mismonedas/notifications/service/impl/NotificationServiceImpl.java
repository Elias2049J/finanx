package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.mapper.NotificationMapper;
import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
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
    private final SecurityService securityService;

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generate(Consumer<Notification.NotificationBuilder> builderConsumer) {
        Notification.NotificationBuilder builder = Notification.builder();
        builderConsumer.accept(builder);

        Notification saved = notificationRepository.save(builder.build());
        NotificationDTO dto = notificationMapper.toResponse(saved);
        if (saved.getUser() != null && saved.getUser().getPreferences() != null) {
            dto.setEmailNotifications(saved.getUser().getPreferences().isEmailNotifications());
        }

        outboxRepository.save(NotificationOutbox.builder()
                .notificationId(saved.getId())
                .type(saved.getType())
                .payload(toJson(dto))
                .state(OutboxState.PENDING)
                .createdAt(OffsetDateTime.now(saved.getUser().getTimeZone().toZoneId()))
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
    @Transactional(readOnly = true)
    public NotificationDTO findById(Long id) {
        return notificationMapper.toResponse(getOwned(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> findAllByUser(Long userId) {
        return notificationRepository.findAllByUser_IdAndStateIsNotOrderByCreatedAtDesc(userId, NotificationState.DISCARD)
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
        Notification n = getOwned(id);
        n.setState(NotificationState.READ);
        notificationRepository.save(n);
    }

    @Transactional
    @Override
    public void discard(Long id) {
        Notification existing = getOwned(id);
        this.disable(existing);
    }

    private Notification getOwned(Long id) {
        Long userId = securityService.getCurrentUser().getId();
        return notificationRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + id));
    }

    private void disable(Notification existing) {
        ZoneId zoneId = existing.getUser().getTimeZone().toZoneId();
        existing.setDisabledAt(OffsetDateTime.now(zoneId));
        existing.setState(NotificationState.DISCARD);
        notificationRepository.save(existing);
    }

    @Override
    @Transactional
    public void purgeOlderThan(LocalDateTime threshold, long userId) {
        User u = userRepository.findById(userId).orElseThrow();
        List<Notification> nList = notificationRepository.findAllByUser_IdAndStateAndSentAtBefore(userId, NotificationState.SENT, OffsetDateTime.now(u.getTimeZone().toZoneId()));
        nList.forEach(this::disable);
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
