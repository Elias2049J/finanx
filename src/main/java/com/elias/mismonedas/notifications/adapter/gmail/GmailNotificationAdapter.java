package com.elias.mismonedas.notifications.adapter.gmail;

import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.port.NotificationPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailNotificationAdapter implements NotificationPort {

    private final GmailProperties gmailProperties;

    @Override
    @CircuitBreaker(name = "gmail")
    public void send(NotificationDTO notification) {
        if (!notification.isEmailNotifications()) {
            log.debug("Email skip: user {} tiene notificaciones email deshabilitadas", notification.getUserId());
            return;
        }
        String subject = "Mis Monedas - " + (notification.getType() != null ? notification.getType().name() : "NOTIFICATION");
        String body = notification.getMessage() != null ? notification.getMessage() : "";

        sendEmail(notification.getUserEmail(), subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        String host = gmailProperties.getHost();
        int port = gmailProperties.getPort();

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(gmailProperties.getUsername(), gmailProperties.getAppPassword());
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            String from = gmailProperties.getFrom();
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");

            Transport.send(message);
            log.info("Email enviado a {} con subject='{}'", to, subject);
        } catch (MessagingException e) {
            throw new IllegalStateException("Error enviando email por SMTP", e);
        }
    }
}
