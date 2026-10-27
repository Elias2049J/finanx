package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.event.UserLoggedOutEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.notifications.repository.FcmTokenRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserNotificationListenerTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final FcmTokenRepository fcmTokenRepository = mock(FcmTokenRepository.class);
    private final NotificationOutboxRepository notificationOutboxRepository = mock(NotificationOutboxRepository.class);
    private final UserNotificationListener listener =
            new UserNotificationListener(notificationRepository, fcmTokenRepository, notificationOutboxRepository);

    @Test
    void shouldDeleteNotificationOutboxBeforeUserNotifications() {
        listener.onUserDeleted(new UserDeletedEvent(17L));

        var order = inOrder(notificationOutboxRepository, notificationRepository, fcmTokenRepository);
        order.verify(notificationOutboxRepository).deleteAllForUser(17L);
        order.verify(notificationRepository).deleteAllByUser_Id(17L);
        order.verify(fcmTokenRepository).deleteAllByUser_Id(17L);
    }

    @Test
    void shouldRemoveOnlyTheFcmTokenProvidedForTheLogoutOwner() {
        listener.onUserLoggedOut(new UserLoggedOutEvent(17L, "device-token"));

        verify(fcmTokenRepository).deleteByTokenAndUserId("device-token", 17L);
        verify(fcmTokenRepository, never()).deleteAllByUser_Id(17L);
    }

    @Test
    void shouldIgnoreLogoutWithoutAnFcmToken() {
        listener.onUserLoggedOut(new UserLoggedOutEvent(17L, " "));

        verify(fcmTokenRepository, never()).deleteByTokenAndUserId(" ", 17L);
    }
}
