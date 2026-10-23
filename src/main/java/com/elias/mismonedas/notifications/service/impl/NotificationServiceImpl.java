package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.SupportedLocale;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.domain.event.NotificationOutboxCreatedEvent;
import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.mapper.NotificationMapper;
import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.notifications.repository.NotificationSpecifications;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final ApplicationEventPublisher eventPublisher;
    private final JsonMessageCatalog messageCatalog;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generate(Consumer<Notification.NotificationBuilder> builderConsumer) {
        Notification.NotificationBuilder builder = Notification.builder();
        builderConsumer.accept(builder);

        Notification notification = builder.build();
        String localeTag = localeTagOf(notification.getUser());
        notification.setLocale(localeTag);
        Notification saved = notificationRepository.save(notification);
        NotificationDTO dto = toLocalizedResponse(saved);
        if (saved.getUser() != null && saved.getUser().getPreferences() != null) {
            dto.setEmailNotifications(saved.getUser().getPreferences().isEmailNotifications());
        }

        NotificationOutbox outbox = outboxRepository.save(NotificationOutbox.builder()
                .notificationId(saved.getId())
                .type(saved.getType())
                .payload(toJson(dto))
                .state(OutboxState.PENDING)
                .createdAt(OffsetDateTime.now(saved.getUser().getTimeZone().toZoneId()))
                .build());

        eventPublisher.publishEvent(new NotificationOutboxCreatedEvent(outbox.getId()));

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
        return toLocalizedResponse(getOwned(id));
    }


    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationDTO> list(NotificationState state, String search, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> page = notificationRepository.findAll(
                NotificationSpecifications.list(userId, state, search), stablePage);
        return PageResponseFactory.from(page, this::toLocalizedResponse);
    }

    private NotificationDTO toLocalizedResponse(Notification notification) {
        NotificationDTO dto = notificationMapper.toResponse(notification);
        String localeTag = notification.getLocale() == null || notification.getLocale().isBlank()
                ? localeTagOf(notification.getUser())
                : SupportedLocale.canonicalizeOrDefault(notification.getLocale());
        dto.setLocale(localeTag);
        String titleKey = notification.getType() == null
                ? "notification.title.INFO"
                : "notification.title." + notification.getType().name();
        dto.setTitle(messageCatalog.getMessage(titleKey, SupportedLocale.toLocale(localeTag)));
        return dto;
    }

    private static String localeTagOf(User user) {
        return SupportedLocale.canonicalizeOrDefault(user == null || user.getPreferences() == null
                ? null
                : user.getPreferences().getLocale());
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
    public void purgeOlderThan(LocalDateTime threshold) {
        User u = securityService.getCurrentUser();
        Long userId = u.getId();
        List<Notification> nList = notificationRepository.findAllByUser_IdAndStateAndSentAtBefore(userId, NotificationState.SENT, OffsetDateTime.now(u.getTimeZone().toZoneId()));
        nList.forEach(this::disable);
        notificationRepository.saveAll(nList);
    }
}
