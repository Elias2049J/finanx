package com.elias.mismonedas.billing.service.impl;

import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.domain.entity.WebhookEvent;
import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import com.elias.mismonedas.billing.domain.enums.WebhookEventState;
import com.elias.mismonedas.billing.dto.webhook.PaymentWebhookNotification;
import com.elias.mismonedas.billing.mapper.PaymentMapper;
import com.elias.mismonedas.billing.port.PaymentPort;
import com.elias.mismonedas.billing.repository.PaymentRepository;
import com.elias.mismonedas.billing.repository.WebhookEventRepository;
import com.elias.mismonedas.billing.service.impl.PaymentServiceImpl;
import com.elias.mismonedas.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    @Captor private ArgumentCaptor<WebhookEvent> eventCaptor;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(
                paymentRepository, webhookEventRepository, userRepository, paymentMapper, paymentPort);
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
    class ProcessWebhookEventTest {

        @Test
        void shouldTreatExistingEventAsIdempotentWithoutSaving() {
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(true);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-1", "REF-1", "approved"));
            when(webhookEventRepository.existsByExternalEventId("EVT-1")).thenReturn(true);

            service.processWebhookEvent(PaymentProvider.MANUAL_TRANSFER, "{}", "sig");

            verify(webhookEventRepository, never()).saveAndFlush(any());
            verify(webhookEventRepository, never()).save(any());
        }

        @Test
        void shouldTreatConcurrentDuplicateConstraintAsIdempotent() {
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(true);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-1", "REF-1", "approved"));
            when(webhookEventRepository.existsByExternalEventId("EVT-1")).thenReturn(false);
            when(webhookEventRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("uq id_evento_externo"));

            service.processWebhookEvent(PaymentProvider.MANUAL_TRANSFER, "{}", "sig");

            verify(webhookEventRepository, never()).save(any());
        }

        @Test
        void shouldMarkIgnoredWhenPaymentNotFound() {
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(true);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-2", "REF-2", "approved"));
            when(webhookEventRepository.existsByExternalEventId("EVT-2")).thenReturn(false);
            when(webhookEventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentRepository.findByExternalReference("REF-2")).thenReturn(Optional.empty());

            service.processWebhookEvent(PaymentProvider.MANUAL_TRANSFER, "{}", "sig");

            verify(webhookEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getState()).isEqualTo(WebhookEventState.IGNORED);
            assertThat(eventCaptor.getValue().getProcessedAt()).isNotNull();
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void shouldApplyTransitionAndMarkProcessed() {
            Payment payment = paymentInState(PaymentState.PENDING);
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(true);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-3", "REF-3", "approved"));
            when(webhookEventRepository.existsByExternalEventId("EVT-3")).thenReturn(false);
            when(webhookEventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentRepository.findByExternalReference("REF-3")).thenReturn(Optional.of(payment));

            service.processWebhookEvent(PaymentProvider.MANUAL_TRANSFER, "{}", "sig");

            assertThat(payment.getState()).isEqualTo(PaymentState.COMPLETED);
            verify(paymentRepository).save(payment);
            verify(webhookEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getState()).isEqualTo(WebhookEventState.PROCESSED);
        }

        @Test
        void shouldBeNoOpWhenPaymentAlreadyInTargetState() {
            Payment payment = paymentInState(PaymentState.COMPLETED);
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(true);
            when(paymentPort.parseWebhookPayload(any())).thenReturn(notification("EVT-4", "REF-4", "approved"));
            when(webhookEventRepository.existsByExternalEventId("EVT-4")).thenReturn(false);
            when(webhookEventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(paymentRepository.findByExternalReference("REF-4")).thenReturn(Optional.of(payment));

            service.processWebhookEvent(PaymentProvider.MANUAL_TRANSFER, "{}", "sig");

            assertThat(payment.getState()).isEqualTo(PaymentState.COMPLETED);
            verify(paymentRepository, never()).save(any());
            verify(webhookEventRepository).save(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getState()).isEqualTo(WebhookEventState.PROCESSED);
        }

        @Test
        void shouldRejectInvalidSignature() {
            when(paymentPort.verifyWebhookSignature(any(), any())).thenReturn(false);

            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> service.processWebhookEvent(PaymentProvider.MANUAL_TRANSFER, "{}", "sig"))
                    .isInstanceOf(SecurityException.class);

            verify(webhookEventRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class ReconcileWebhookEventsTest {

        @Test
        void shouldReplayFailedEventAndMarkProcessed() {
            WebhookEvent failed = WebhookEvent.builder()
                    .id(50L)
                    .provider(PaymentProvider.MANUAL_TRANSFER)
                    .externalEventId("EVT-9")
                    .rawPayload("{}")
                    .state(WebhookEventState.FAILED)
                    .build();
            Payment payment = paymentInState(PaymentState.PENDING);

            when(webhookEventRepository.findReconcilable(
                    eq(WebhookEventState.RECEIVED), eq(WebhookEventState.FAILED), any(), any(Pageable.class)))
                    .thenReturn(List.of(failed));
            when(paymentPort.parseWebhookPayload("{}")).thenReturn(notification("EVT-9", "REF-9", "approved"));
            when(paymentRepository.findByExternalReference("REF-9")).thenReturn(Optional.of(payment));

            int reconciled = service.reconcileWebhookEvents();

            assertThat(reconciled).isEqualTo(1);
            assertThat(payment.getState()).isEqualTo(PaymentState.COMPLETED);
            assertThat(failed.getState()).isEqualTo(WebhookEventState.PROCESSED);
            assertThat(failed.getErrorMessage()).isNull();
            verify(webhookEventRepository).save(failed);
        }

        @Test
        void shouldKeepEventFailedWhenReplayThrows() {
            WebhookEvent failed = WebhookEvent.builder()
                    .id(51L)
                    .provider(PaymentProvider.MANUAL_TRANSFER)
                    .externalEventId("EVT-10")
                    .rawPayload("bad")
                    .state(WebhookEventState.FAILED)
                    .build();

            when(webhookEventRepository.findReconcilable(
                    eq(WebhookEventState.RECEIVED), eq(WebhookEventState.FAILED), any(), any(Pageable.class)))
                    .thenReturn(List.of(failed));
            when(paymentPort.parseWebhookPayload("bad")).thenThrow(new IllegalStateException("bad payload"));

            int reconciled = service.reconcileWebhookEvents();

            assertThat(reconciled).isZero();
            assertThat(failed.getState()).isEqualTo(WebhookEventState.FAILED);
            assertThat(failed.getErrorMessage()).isEqualTo("bad payload");
            verify(webhookEventRepository).save(failed);
        }
    }
}
