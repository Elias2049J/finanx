package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.mapper.NotificationMapper;
import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private UserRepository userRepository;
    @Mock private SecurityService securityService;
    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JsonMessageCatalog messageCatalog = new JsonMessageCatalog(objectMapper);
        messageCatalog.initialize();
        service = new NotificationServiceImpl(notificationRepository, notificationMapper, userRepository,
                securityService, outboxRepository, objectMapper, eventPublisher, messageCatalog);
    }

    @Test
    void shouldPersistLocalizedSnapshotInOutboxPayload() throws Exception {
        User user = new User();
        user.setId(7L);
        user.setPreferences(UserPreferences.builder()
                .locale("en-US")
                .timeZone(TimeZone.AMERICA_LIMA)
                .emailNotifications(true)
                .build());

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(91L);
            return notification;
        });
        when(notificationMapper.toResponse(any(Notification.class))).thenReturn(NotificationDTO.builder()
                .id(91L)
                .userId(7L)
                .type(NotificationType.WARNING)
                .message("You exceeded your budget.")
                .htmlMessage("<p>Budget exceeded</p>")
                .build());
        when(outboxRepository.save(any(NotificationOutbox.class))).thenAnswer(invocation -> {
            NotificationOutbox outbox = invocation.getArgument(0);
            outbox.setId(301L);
            return outbox;
        });

        service.generate(builder -> builder
                .user(user)
                .type(NotificationType.WARNING)
                .message("You exceeded your budget.")
                .htmlMessage("<p>Budget exceeded</p>"));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getLocale()).isEqualTo("en-US");

        ArgumentCaptor<NotificationOutbox> outboxCaptor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        NotificationDTO payload = objectMapper.readValue(outboxCaptor.getValue().getPayload(), NotificationDTO.class);
        assertThat(payload.getLocale()).isEqualTo("en-US");
        assertThat(payload.getTitle()).isEqualTo("Mis Monedas - Notice");
        assertThat(payload.getMessage()).isEqualTo("You exceeded your budget.");
        assertThat(payload.isEmailNotifications()).isTrue();
    }
}
