package com.elias.mismonedas.notifications.adapter.fcm;

import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.port.NotificationPort;
import com.elias.mismonedas.notifications.repository.FcmTokenRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FCMNotificationAdapter implements NotificationPort {
    private final FCMProperties fcmProperties;
    private final FcmTokenRepository fcmTokenRepository;

    @PostConstruct
    public void init() {
        if (FirebaseApp.getApps().stream().anyMatch(app -> app.getName().equals(FirebaseApp.DEFAULT_APP_NAME))) {
            log.info("FirebaseApp ya inicializado");
            return;
        }
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(new FileInputStream(fcmProperties.credentialsPath())))
                    .setProjectId(fcmProperties.projectId())
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("FirebaseApp inicializado correctamente");
        } catch (IOException e) {
            log.error("Error al inicializar FirebaseApp con credentialsPath={}", fcmProperties.credentialsPath(), e);
        }
    }

    @Override
    @CircuitBreaker(name = "fcm")
    public void send(NotificationDTO notification) {
        List<String> tokens = notification.getFcmTokens();
        if (tokens == null || tokens.isEmpty()) {
            log.warn("Sin tokens FCM para userId={}, se omite envio push", notification.getUserId());
            return;
        }

        String title = "Mis Monedas - " + (notification.getType() != null ? notification.getType().name() : "NOTIFICATION");
        String body = notification.getMessage() != null ? notification.getMessage() : "";

        Map<String, String> data = new HashMap<>();
        if (notification.getId() != null) data.put("notificationId", notification.getId().toString());
        if (notification.getUserId() != null) data.put("userId", notification.getUserId().toString());
        if (notification.getType() != null) data.put("type", notification.getType().name());
        if (notification.getMessage() != null) data.put("message", notification.getMessage());
        if (notification.getBudgetId() != null) data.put("budgetId", notification.getBudgetId().toString());
        if (notification.getBudgetDescription() != null) data.put("budgetDescription", notification.getBudgetDescription());
        if (notification.getBudgetState() != null) data.put("budgetState", notification.getBudgetState().name());
        if (notification.getSavingGoalId() != null) data.put("savingGoalId", notification.getSavingGoalId().toString());
        if (notification.getSavingGoalDescription() != null) data.put("savingGoalDescription", notification.getSavingGoalDescription());

        List<Message> messages = tokens.stream()
                .map(token -> Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putAllData(data)
                        .build())
                .toList();

        BatchResponse response;
        try {
            response = FirebaseMessaging.getInstance().sendEach(messages);
        } catch (FirebaseMessagingException e) {
            log.error("Error enviando lote FCM a userId={}, sera re-intentado", notification.getUserId(), e);
            throw new RuntimeException("FCM batch failed for userId=" + notification.getUserId(), e);
        }
        log.info("FCM enviada a userId={}, exito={}, fallo={}",
                notification.getUserId(), response.getSuccessCount(), response.getFailureCount());

        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) continue;

            FirebaseMessagingException ex = sendResponse.getException();
            if (ex == null) continue;

            MessagingErrorCode errorCode = ex.getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                String failedToken = tokens.get(i);
                log.warn("Token FCM invalido ({}), eliminando: {}", errorCode, failedToken);
                fcmTokenRepository.findByToken(failedToken)
                        .ifPresent(t -> fcmTokenRepository.deleteById(t.getId()));
            } else {
                log.warn("Error transitorio en token FCM ({}): {}", errorCode, tokens.get(i));
            }
        }
    }
}
