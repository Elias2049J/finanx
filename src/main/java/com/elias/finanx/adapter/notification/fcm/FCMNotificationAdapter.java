package com.elias.finanx.adapter.notification.fcm;

import com.elias.finanx.port.NotificationPort;
import com.elias.finanx.dto.notification.NotificationDTO;
import com.elias.finanx.repository.FcmTokenRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

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
    public void send(NotificationDTO notification) {
        List<String> tokens = notification.getFcmTokens();
        if (tokens == null || tokens.isEmpty()) {
            log.warn("Sin tokens FCM para userId={}, se omite envio push", notification.getUserId());
            return;
        }

        String title = "Finanx - " + (notification.getType() != null ? notification.getType().name() : "NOTIFICATION");
        String body = notification.getMessage() != null ? notification.getMessage() : "";

        List<Message> messages = tokens.stream()
                .map(token -> Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .build())
                .toList();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEach(messages);
            log.info("FCM enviada a userId={}, exito={}, fallo={}",
                    notification.getUserId(), response.getSuccessCount(), response.getFailureCount());

            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse sendResponse = responses.get(i);
                if (sendResponse.isSuccessful()) continue;

                FirebaseMessagingException ex = sendResponse.getException();
                if (ex != null && ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    String failedToken = tokens.get(i);
                    log.warn("Token FCM no registrado, eliminando: {}", failedToken);
                    fcmTokenRepository.deleteByToken(failedToken);
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("Error enviando lote FCM a userId={}", notification.getUserId(), e);
        }
    }
}
