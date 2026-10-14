package com.elias.mismonedas.notifications.port;

import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;

public interface NotificationPort {
    void send(NotificationDTO notification);
}
