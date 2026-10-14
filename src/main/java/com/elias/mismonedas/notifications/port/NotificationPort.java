package com.elias.mismonedas.notifications.port;

import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.outbox.NotificationDeliveryChannel;

public interface NotificationPort {
    NotificationDeliveryChannel channel();

    void send(NotificationDTO notification);
}
