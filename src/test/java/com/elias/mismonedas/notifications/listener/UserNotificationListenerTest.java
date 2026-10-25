package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.identity.domain.event.UserLoggedOutEvent;
import com.elias.mismonedas.notifications.repository.FcmTokenRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserNotificationListenerTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final FcmTokenRepository fcmTokenRepository = mock(FcmTokenRepository.class);
    private final UserNotificationListener listener =
            new UserNotificationListener(notificationRepository, fcmTokenRepository);

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
