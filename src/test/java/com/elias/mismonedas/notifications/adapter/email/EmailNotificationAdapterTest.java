package com.elias.mismonedas.notifications.adapter.email;

import com.elias.mismonedas.identity.util.InvalidEmailAddressException;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationAdapterTest {

    @Mock private EmailProperties emailProperties;
    @Mock private JsonMessageCatalog messageCatalog;

    @Test
    void shouldSendOneTransactionalCodeRegardlessOfNotificationPreferences() throws Exception {
        when(emailProperties.getHost()).thenReturn("smtp.example.test");
        when(emailProperties.getPort()).thenReturn(465);
        when(emailProperties.getFrom()).thenReturn("no-reply@example.test");
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(emailProperties, messageCatalog);
        AtomicReference<Message> sentMessage = new AtomicReference<>();

        try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
            transport.when(() -> Transport.send(any(Message.class))).thenAnswer(invocation -> {
                sentMessage.set(invocation.getArgument(0));
                return null;
            });

            adapter.sendVerificationCode("alice@example.test", "001234", Duration.ofMinutes(15));
        }

        assertThat(sentMessage.get().getRecipients(Message.RecipientType.TO))
                .hasSize(1)
                .extracting("address")
                .containsExactly("alice@example.test");
        assertThat(sentMessage.get().getSubject()).isEqualTo("Verifica tu correo de Finanx");
        assertThat(sentMessage.get().getContent().toString())
                .contains("001234")
                .contains("15 minutos");
    }

    @Test
    void shouldRejectMultipleRecipientsBeforeCallingSmtp() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(emailProperties, messageCatalog);

        try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
            assertThatThrownBy(() -> adapter.sendVerificationCode(
                    "alice@example.test,bob@example.test", "001234", Duration.ofMinutes(15)))
                    .isInstanceOf(InvalidEmailAddressException.class);

            transport.verifyNoInteractions();
        }
    }

    @Test
    void shouldWrapSmtpFailuresAsTransportFailures() {
        when(emailProperties.getHost()).thenReturn("smtp.example.test");
        when(emailProperties.getPort()).thenReturn(465);
        when(emailProperties.getFrom()).thenReturn("no-reply@example.test");
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(emailProperties, messageCatalog);

        try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
            transport.when(() -> Transport.send(any(Message.class)))
                    .thenThrow(new MessagingException("smtp unavailable"));

            assertThatThrownBy(() -> adapter.sendVerificationCode(
                    "alice@example.test", "001234", Duration.ofMinutes(15)))
                    .isInstanceOf(EmailTransportException.class);
        }
    }
}
