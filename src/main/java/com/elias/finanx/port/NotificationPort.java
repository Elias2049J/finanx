package com.elias.finanx.port;

import com.elias.finanx.dto.notification.NotificationDTO;

public interface NotificationPort {
    void send(NotificationDTO notification);
}
