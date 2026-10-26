package com.elias.mismonedas.notifications.adapter.fcm;

import com.elias.mismonedas.notifications.repository.FcmTokenRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FCMNotificationAdapterTest {

    @Mock private FcmTokenRepository fcmTokenRepository;
    @Mock private BatchResponse batchResponse;
    @Mock private SendResponse successfulResponse;
    @Mock private SendResponse invalidTokenResponse;
    @Mock private FirebaseMessagingException messagingException;

    @Test
    void shouldDeleteOnlyTokensThatFirebaseConfirmsAreInvalid() {
        assertThat(FCMNotificationAdapter.isInvalidTokenError(MessagingErrorCode.UNREGISTERED)).isTrue();
        assertThat(FCMNotificationAdapter.isInvalidTokenError(MessagingErrorCode.SENDER_ID_MISMATCH)).isTrue();
        assertThat(FCMNotificationAdapter.isInvalidTokenError(MessagingErrorCode.INVALID_ARGUMENT)).isFalse();
        assertThat(FCMNotificationAdapter.isInvalidTokenError(null)).isFalse();
    }

    @Test
    void shouldRefreshUpdatedAtOnlyForDeliveredTokens() {
        when(successfulResponse.isSuccessful()).thenReturn(true);
        when(invalidTokenResponse.isSuccessful()).thenReturn(false);
        when(invalidTokenResponse.getException()).thenReturn(messagingException);
        when(messagingException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(batchResponse.getResponses()).thenReturn(List.of(successfulResponse, invalidTokenResponse));

        FCMNotificationAdapter adapter = new FCMNotificationAdapter(null, fcmTokenRepository, null);
        adapter.applyBatchResponse(7L, List.of("delivered-token", "invalid-token"), batchResponse);

        verify(fcmTokenRepository).touchByTokens(
                eq(List.of("delivered-token")), eq(7L), any(LocalDateTime.class));
        verify(fcmTokenRepository).deleteByTokenAndUserId("invalid-token", 7L);
    }
}
