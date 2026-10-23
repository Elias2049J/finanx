package com.elias.mismonedas.notifications.adapter.email;

import com.elias.mismonedas.identity.domain.enums.SupportedLocale;
import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.outbox.NotificationDeliveryChannel;
import com.elias.mismonedas.notifications.port.NotificationPort;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationAdapter implements NotificationPort {

    private final EmailProperties emailProperties;
    private final JsonMessageCatalog messageCatalog;

    @Override
    public NotificationDeliveryChannel channel() {
        return NotificationDeliveryChannel.EMAIL;
    }

    @Override
    @CircuitBreaker(name = "email")
    public void send(NotificationDTO notification) {
        if (!notification.isEmailNotifications()) {
            log.debug("Email skip: user {} tiene notificaciones email deshabilitadas", notification.getUserId());
            return;
        }
        String subject = localizedTitle(notification);
        String text = notification.getMessage() != null ? notification.getMessage() : "";
        String html = notification.getHtmlMessage();

        sendEmail(notification.getUserEmail(), subject, text, html);
    }

    private String localizedTitle(NotificationDTO notification) {
        if (notification.getTitle() != null) {
            return notification.getTitle();
        }
        String type = notification.getType() == null ? "INFO" : notification.getType().name();
        return messageCatalog.getMessage(
                "notification.title." + type,
                SupportedLocale.toLocale(notification.getLocale()));
    }

    private void sendEmail(String to, String subject, String text, String html) {
        String host = emailProperties.getHost();
        int port = emailProperties.getPort();

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(emailProperties.getUsername(), emailProperties.getAppPassword());
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            String from = emailProperties.getFrom();
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject, "UTF-8");

            if (html != null && !html.isBlank()) {
                MimeMultipart multipart = new MimeMultipart("alternative");
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(text, "UTF-8");
                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(html, "text/html; charset=UTF-8");
                multipart.addBodyPart(textPart);
                multipart.addBodyPart(htmlPart);
                message.setContent(multipart);
            } else {
                message.setText(text, "UTF-8");
            }

            Transport.send(message);
            log.info("Email enviado a {} con subject='{}'", to, subject);
        } catch (MessagingException e) {
            throw new IllegalStateException("Error enviando email por SMTP", e);
        }
    }
}
