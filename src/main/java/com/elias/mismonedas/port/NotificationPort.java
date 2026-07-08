package com.elias.mismonedas.port;

import com.elias.mismonedas.dto.notification.NotificationDTO;

public interface NotificationPort {
    void send(NotificationDTO notification);
}
