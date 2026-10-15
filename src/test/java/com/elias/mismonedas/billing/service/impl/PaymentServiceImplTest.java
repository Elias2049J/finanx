package com.elias.mismonedas.billing.service.impl;

import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.domain.entity.WebhookEvent;
import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import com.elias.mismonedas.billing.domain.enums.WebhookEventState;
import com.elias.mismonedas.billing.dto.webhook.PaymentWebhookNotification;
import com.elias.mismonedas.billing.exception.InvalidWebhookSignatureException;
import com.elias.mismonedas.billing.mapper.PaymentMapper;
import com.elias.mismonedas.billing.port.PaymentPort;
import com.elias.mismonedas.billing.port.PaymentWebhookPublisher;
import com.elias.mismonedas.billing.repository.PaymentRepository;
import com.elias.mismonedas.billing.repository.WebhookEventRepository;
import com.elias.mismonedas.billing.service.impl.PaymentServiceImpl;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private WebhookEventRepository webhookEventRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PaymentPort paymentPort;
    @Mock private PaymentWebhookPublisher webhookPublisher;
    @Mock private SecurityService securityService;

    @Captor private ArgumentCaptor<WebhookEvent> eventCaptor;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(
                paymentRepository, webhookEventRepository, userRepository, paymentMapper, paymentPort, webhookPublisher,
                securityService);
    }

    private PaymentWebhookNotification notification(String externalEventId, String externalReference, String status) {
        return PaymentWebhookNotification.builder()
                .provider(PaymentProvider.MANUAL_TRANSFER)
                .externalEventId(externalEventId)
                .externalReference(externalReference)
                .eventType("payment.updated")
                .status(status)
                .build();
    }

    private Payment paymentInState(PaymentState state) {
        return Payment.builder().id(10L).state(state).build();
    }

    @Nested
    class ReceiveWebhookEventTest {

        @Test
        void shouldTreatExistingEventAsIdempotentWithoutPublishingAgain() {
            WebhookEvent existing = WebhookEvent.builder()
                    .id(1L)
                    .externalEventId("EVT-1")
                    .state(WebhookEventState.RECEIVED)
                    .build();
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(true);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-1", "REF-1", "approved"));
            when(webhookEventRepository.findByExternalEventId("EVT-1")).thenReturn(Optional.of(existing));

            service.receiveWebhookEvent(PaymentProvider.MANUAL_TRANSFER, "{}", "sig");

            verify(webhookEventRepository, never()).saveAndFlush(any());
            verify(webhookPublisher, never()).publish(any());
        }

        @Test
        void shouldTreatConcurrentDuplicateConstraintAsIdempotent() {
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(true);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-1", "REF-1", "approved"));
            when(webhookEventRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("uq id_evento_externo"));
            when(webhookEventRepository.findByExternalEventId("EVT-1"))
                    .thenReturn(Optional.empty(), Optional.of(WebhookEvent.builder().id(1L).build()));

            service.receiveWebhookEvent(PaymentProvider.MANUAL_TRANSFER, "{}", "sig");

            verify(webhookEventRepository, never()).save(any());
            verify(webhookPublisher, never()).publish(any());
        }

        @Test
        void shouldPersistReceivedAndPublishOnlyTheEventId() {
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(true);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-2", "REF-2", "approved"));
            when(webhookEventRepository.findByExternalEventId("EVT-2")).thenReturn(Optional.empty());
            when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> {
                WebhookEvent event = invocation.getArgument(0);
                event.setId(2L);
                return event;
            });

            service.receiveWebhookEvent(PaymentProvider.MANUAL_TRANSFER, "{}", "sig");

            verify(webhookEventRepository).saveAndFlush(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getState()).isEqualTo(WebhookEventState.RECEIVED);
            verify(webhookPublisher).publish(2L);
            verify(paymentRepository, never()).findByExternalReference(any());
        }

        @Test
        void shouldKeepDurableReceiptAndReturnNormallyWhenBrokerIsUnavailable() {
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(true);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-2B", "REF-2B", "approved"));
            when(webhookEventRepository.findByExternalEventId("EVT-2B")).thenReturn(Optional.empty());
            when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> {
                WebhookEvent event = invocation.getArgument(0);
                event.setId(22L);
                return event;
            });
            doThrow(new AmqpException("broker unavailable")).when(webhookPublisher).publish(22L);

            service.receiveWebhookEvent(PaymentProvider.MANUAL_TRANSFER, "{}", "sig");

            verify(webhookEventRepository).saveAndFlush(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getState()).isEqualTo(WebhookEventState.RECEIVED);
            verify(webhookPublisher).publish(22L);
        }

        @Test
        void shouldRejectInvalidSignatureWithWebhookSpecificException() {
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(false);

            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> service.receiveWebhookEvent(
                            PaymentProvider.MANUAL_TRANSFER, "{}", "sig"))
                    .isInstanceOf(InvalidWebhookSignatureException.class);

            verify(webhookEventRepository, never()).saveAndFlush(any());
        }

        @Test
        void shouldRejectPayloadWithoutExternalEventId() {
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(true);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification(null, "REF-3", "approved"));

            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> service.receiveWebhookEvent(
                            PaymentProvider.MANUAL_TRANSFER, "{}", "sig"))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(webhookEventRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class ProcessQueuedWebhookEventTest {

        @Test
        void shouldApplyTransitionAndMarkProcessed() {
            WebhookEvent event = WebhookEvent.builder()
                    .id(3L)
                    .externalEventId("EVT-3")
                    .rawPayload("{}")
                    .state(WebhookEventState.RECEIVED)
                    .build();
            Payment payment = paymentInState(PaymentState.PENDING);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-3", "REF-3", "approved"));
            when(webhookEventRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(event));
            when(paymentRepository.findByExternalReference("REF-3")).thenReturn(Optional.of(payment));

            service.processWebhookEvent(3L);

            assertThat(payment.getState()).isEqualTo(PaymentState.COMPLETED);
            verify(paymentRepository).save(payment);
            assertThat(event.getState()).isEqualTo(WebhookEventState.PROCESSED);
        }

        @Test
        void shouldBeNoOpWhenPaymentAlreadyInTargetState() {
            WebhookEvent event = WebhookEvent.builder()
                    .id(4L)
                    .externalEventId("EVT-4")
                    .rawPayload("{}")
                    .state(WebhookEventState.RECEIVED)
                    .build();
            Payment payment = paymentInState(PaymentState.COMPLETED);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-4", "REF-4", "approved"));
            when(webhookEventRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(event));
            when(paymentRepository.findByExternalReference("REF-4")).thenReturn(Optional.of(payment));

            service.processWebhookEvent(4L);

            assertThat(payment.getState()).isEqualTo(PaymentState.COMPLETED);
            verify(paymentRepository, never()).save(any());
            assertThat(event.getState()).isEqualTo(WebhookEventState.PROCESSED);
        }

        @Test
        void shouldMarkUnknownPaymentAsIgnored() {
            WebhookEvent event = WebhookEvent.builder()
                    .id(6L)
                    .externalEventId("EVT-6")
                    .rawPayload("{}")
                    .state(WebhookEventState.RECEIVED)
                    .build();
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-6", "REF-6", "approved"));
            when(webhookEventRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(event));
            when(paymentRepository.findByExternalReference("REF-6")).thenReturn(Optional.empty());

            service.processWebhookEvent(6L);

            assertThat(event.getState()).isEqualTo(WebhookEventState.IGNORED);
            assertThat(event.getProcessedAt()).isNotNull();
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void shouldIgnoreTerminalEventDelivery() {
            WebhookEvent event = WebhookEvent.builder()
                    .id(5L)
                    .state(WebhookEventState.PROCESSED)
                    .build();
            when(webhookEventRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(event));

            service.processWebhookEvent(5L);

            verify(paymentPort, never()).parseWebhookPayload(any());
            verify(paymentRepository, never()).findByExternalReference(any());
        }
    }

    @Nested
    class ReconcileWebhookEventsTest {

        @Test
        void shouldRepublishStaleReceivedEventWithoutProcessingInline() {
            WebhookEvent received = WebhookEvent.builder()
                    .id(50L)
                    .provider(PaymentProvider.MANUAL_TRANSFER)
                    .externalEventId("EVT-9")
                    .rawPayload("{}")
                    .state(WebhookEventState.RECEIVED)
                    .build();

            when(webhookEventRepository.findStaleReceived(eq(WebhookEventState.RECEIVED), any(), any(Pageable.class)))
                    .thenReturn(List.of(received));

            int published = service.reconcileWebhookEvents();

            assertThat(published).isEqualTo(1);
            assertThat(received.getState()).isEqualTo(WebhookEventState.RECEIVED);
            verify(webhookPublisher).publish(50L);
            verify(paymentPort, never()).parseWebhookPayload(any());
        }

        @Test
        void shouldKeepReceivedEventWhenBrokerPublishFails() {
            WebhookEvent received = WebhookEvent.builder()
                    .id(51L)
                    .provider(PaymentProvider.MANUAL_TRANSFER)
                    .externalEventId("EVT-10")
                    .rawPayload("bad")
                    .state(WebhookEventState.RECEIVED)
                    .build();

            when(webhookEventRepository.findStaleReceived(eq(WebhookEventState.RECEIVED), any(), any(Pageable.class)))
                    .thenReturn(List.of(received));
            doThrow(new AmqpException("broker down")).when(webhookPublisher).publish(51L);

            int published = service.reconcileWebhookEvents();

            assertThat(published).isZero();
            assertThat(received.getState()).isEqualTo(WebhookEventState.RECEIVED);
            verify(paymentPort, never()).parseWebhookPayload(any());
        }

        @Test
        void shouldMarkEventFailedAfterBrokerRetriesAreExhausted() {
            WebhookEvent event = WebhookEvent.builder()
                    .id(52L)
                    .state(WebhookEventState.RECEIVED)
                    .build();
            when(webhookEventRepository.findByIdForUpdate(52L)).thenReturn(Optional.of(event));

            service.markWebhookEventFailed(52L, "processing failed");

            assertThat(event.getState()).isEqualTo(WebhookEventState.FAILED);
            assertThat(event.getErrorMessage()).isEqualTo("processing failed");
        }
    }
}
