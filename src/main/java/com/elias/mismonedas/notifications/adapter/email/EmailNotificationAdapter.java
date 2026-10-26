package com.elias.mismonedas.notifications.adapter.email;

import com.elias.mismonedas.identity.domain.enums.SupportedLocale;
import com.elias.mismonedas.identity.service.EmailVerificationEmailSender;
import com.elias.mismonedas.identity.util.EmailAddressValidator;
import com.elias.mismonedas.identity.util.InvalidEmailAddressException;
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

import java.time.Duration;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationAdapter implements NotificationPort, EmailVerificationEmailSender {

    private final EmailProperties emailProperties;
    private final JsonMessageCatalog messageCatalog;

    @Override
    public NotificationDeliveryChannel channel() {
        return NotificationDeliveryChannel.EMAIL;
    }

    @Override
    @CircuitBreaker(name = "email")
    public void send(NotificationDTO notification, String idempotencyKey) {
        if (!notification.isEmailNotifications()) {
            log.debug("Email skip: user {} tiene notificaciones email deshabilitadas", notification.getUserId());
            return;
        }
        String subject = localizedTitle(notification);
        String text = notification.getMessage() != null ? notification.getMessage() : "";
        String html = notification.getHtmlMessage();

        sendEmail(notification.getUserEmail(), subject, text, html, idempotencyKey);
    }

    @Override
    @CircuitBreaker(name = "email")
    public void sendVerificationCode(String email, String code, Duration validity) {
        String text = "Tu código de verificación de Finanx es: " + code
                + "\n\nEl código vence en " + validity.toMinutes()
                + " minutos. Si no solicitaste este código, puedes ignorar este mensaje.";
        sendEmail(email, "Verifica tu correo de Finanx", text, null, null);
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

    private void sendEmail(String to, String subject, String text, String html, String idempotencyKey) {
        String recipientEmail = EmailAddressValidator.normalize(to);
        String host = emailProperties.getHost();
        int port = emailProperties.getPort();

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

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
            InternetAddress recipient = new InternetAddress(recipientEmail, true);
            recipient.validate();
            message.setRecipient(Message.RecipientType.TO, recipient);
            message.setSubject(subject, "UTF-8");
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                message.setHeader("X-Finanx-Delivery-Key", idempotencyKey);
            }

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
            log.info("Email enviado por SMTP");
        } catch (SendFailedException e) {
            if (e.getInvalidAddresses() != null && e.getInvalidAddresses().length > 0) {
                throw new InvalidEmailAddressException();
            }
            throw new EmailTransportException(e);
        } catch (MessagingException e) {
            throw new EmailTransportException(e);
        }
    }
}
