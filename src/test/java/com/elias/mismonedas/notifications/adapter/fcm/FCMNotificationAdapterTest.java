package com.elias.mismonedas.notifications.adapter.fcm;

import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FCMNotificationAdapterTest {

    @Test
    void shouldDeleteOnlyTokensThatFirebaseConfirmsAreInvalid() {
        assertThat(FCMNotificationAdapter.isInvalidTokenError(MessagingErrorCode.UNREGISTERED)).isTrue();
        assertThat(FCMNotificationAdapter.isInvalidTokenError(MessagingErrorCode.SENDER_ID_MISMATCH)).isTrue();
        assertThat(FCMNotificationAdapter.isInvalidTokenError(MessagingErrorCode.INVALID_ARGUMENT)).isFalse();
        assertThat(FCMNotificationAdapter.isInvalidTokenError(null)).isFalse();
    }
}
