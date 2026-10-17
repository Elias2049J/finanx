package com.elias.mismonedas.document.service.impl;

import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.document.domain.entity.Image;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.domain.enums.ReceiptScanStatus;
import com.elias.mismonedas.document.dto.ReceiptResponse;
import com.elias.mismonedas.document.dto.scan.ReceiptScanResultMessage;
import com.elias.mismonedas.document.outbox.ReceiptScanJobDispatcher;
import com.elias.mismonedas.document.outbox.ReceiptScanOutbox;
import com.elias.mismonedas.document.outbox.ReceiptScanOutboxState;
import com.elias.mismonedas.document.outbox.persistence.ReceiptScanOutboxRepository;
import com.elias.mismonedas.document.port.ReceiptData;
import com.elias.mismonedas.document.port.ReceiptParserPort;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import com.elias.mismonedas.document.repository.ReceiptRepository;
import com.elias.mismonedas.document.service.ImageService;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.shared.exception.OcrProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptScanServiceImplTest {

    @Mock private ReceiptParserPort regexParser;
    @Mock private ReceiptParserPort geminiParser;
    @Mock private ReceiptRepository receiptRepository;
    @Mock private ImageService imageService;
    @Mock private SecurityService securityService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ReceiptScanOutboxRepository outboxRepository;
    @Mock private ReceiptScanJobDispatcher jobDispatcher;

    private ReceiptScanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReceiptScanServiceImpl(
                regexParser, geminiParser, receiptRepository, imageService,
                securityService, categoryRepository, outboxRepository, jobDispatcher);
    }

    @Nested
    class StartScanTest {

        @Test
        void shouldPersistPendingReceiptAndEnqueueOutbox() {
            byte[] image = "fake-image-bytes".getBytes();
            User user = createTestUser();
            when(securityService.getCurrentUser()).thenReturn(user);

            Image savedImage = Image.builder()
                    .id(10L).user(user).filename("recibo.jpg").mediaType("image/jpeg")
                    .bucketName("finanx-images").objectKey("images/1/receipt-1.jpg")
                    .sizeBytes((long) image.length).build();
            when(imageService.save(any(User.class), any(), any(), any())).thenReturn(savedImage);

            when(receiptRepository.save(any(Receipt.class))).thenAnswer(invocation -> {
                Receipt receipt = invocation.getArgument(0);
                receipt.setId(1L);
                return receipt;
            });

            ReceiptResponse result = service.startScan(image, "recibo.jpg", "image/jpeg");

            assertThat(result.getReceiptId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(ReceiptScanStatus.PENDING);
            assertThat(result.getImageId()).isEqualTo(10L);

            ArgumentCaptor<ReceiptScanOutbox> outboxCaptor = ArgumentCaptor.forClass(ReceiptScanOutbox.class);
            verify(outboxRepository).save(outboxCaptor.capture());
            ReceiptScanOutbox outbox = outboxCaptor.getValue();
            assertThat(outbox.getReceiptId()).isEqualTo(1L);
            assertThat(outbox.getBucket()).isEqualTo("finanx-images");
            assertThat(outbox.getObjectKey()).isEqualTo("images/1/receipt-1.jpg");
            assertThat(outbox.getState()).isEqualTo(ReceiptScanOutboxState.PENDING);
        }

        @Test
        void shouldThrowWhenNoAuthenticatedUser() {
            when(securityService.getCurrentUser())
                    .thenThrow(new AuthenticationCredentialsNotFoundException("No authenticated user"));

            assertThatThrownBy(() -> service.startScan("test".getBytes(), "test.jpg", "image/jpeg"))
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

            verify(imageService, never()).save(any(), any(), any(), any());
            verify(outboxRepository, never()).save(any());
        }
    }

    @Nested
    class ApplyScanResultTest {

        @Test
        void shouldParseAndCompleteReceiptWhenOcrSucceeds() {
            Receipt receipt = pendingReceipt(1L);
            when(receiptRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(receipt));
            when(regexParser.parse("TOTAL S/. 45.00")).thenReturn(new ReceiptData(
                    new BigDecimal("45.00"), LocalDate.of(2026, 7, 1), "Supermercado",
                    "PEN", PaymentMethod.CASH, TransactionType.SPENT, 0.6, null));

            service.applyScanResult(new ReceiptScanResultMessage(
                    1L, "DONE", "TOTAL S/. 45.00", "rapidocr-ppocrv6", 0.93, 1500L, null));

            assertThat(receipt.getStatus()).isEqualTo(ReceiptScanStatus.DONE);
            assertThat(receipt.getDetectedAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
            assertThat(receipt.getDescription()).isEqualTo("Supermercado");
            assertThat(receipt.getRawOcrText()).isEqualTo("TOTAL S/. 45.00");
            assertThat(receipt.getOcrDurationMs()).isEqualTo(1500L);
            assertThat(receipt.getErrorMessage()).isNull();
            verify(geminiParser, never()).parse(any());
            verify(receiptRepository).save(receipt);
        }

        @Test
        void shouldFallbackToGeminiWhenRegexConfidenceIsLow() {
            Receipt receipt = pendingReceipt(1L);
            when(receiptRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(receipt));
            when(regexParser.parse("texto ambiguo")).thenReturn(
                    new ReceiptData(null, null, null, "PEN", null, null, 0.0, null));
            when(geminiParser.parse("texto ambiguo")).thenReturn(new ReceiptData(
                    new BigDecimal("32.50"), LocalDate.of(2026, 7, 2), "Restaurante",
                    "PEN", PaymentMethod.CREDIT_CARD, TransactionType.SPENT, 0.85, null));

            service.applyScanResult(new ReceiptScanResultMessage(
                    1L, "DONE", "texto ambiguo", "rapidocr-ppocrv6", 0.4, 2000L, null));

            assertThat(receipt.getStatus()).isEqualTo(ReceiptScanStatus.DONE);
            assertThat(receipt.getDescription()).isEqualTo("Restaurante");
            assertThat(receipt.getParserEngineUsed()).isEqualTo("GEMINI");
        }

        @Test
        void shouldMarkFailedWhenWorkerReportsFailure() {
            Receipt receipt = pendingReceipt(1L);
            when(receiptRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(receipt));

            service.applyScanResult(new ReceiptScanResultMessage(
                    1L, "FAILED", null, "rapidocr-ppocrv6", null, 800L, "imagen corrupta"));

            assertThat(receipt.getStatus()).isEqualTo(ReceiptScanStatus.FAILED);
            assertThat(receipt.getErrorMessage()).isEqualTo("imagen corrupta");
            verify(regexParser, never()).parse(any());
        }

        @Test
        void shouldFailWhenAmountCannotBeExtracted() {
            Receipt receipt = pendingReceipt(1L);
            when(receiptRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(receipt));
            when(regexParser.parse("texto sin monto")).thenReturn(
                    new ReceiptData(null, null, null, "PEN", null, null, 0.0, null));
            when(geminiParser.parse("texto sin monto")).thenThrow(new RuntimeException("Gemini error"));

            service.applyScanResult(new ReceiptScanResultMessage(
                    1L, "DONE", "texto sin monto", "rapidocr-ppocrv6", 0.3, 1000L, null));

            assertThat(receipt.getStatus()).isEqualTo(ReceiptScanStatus.FAILED);
            assertThat(receipt.getErrorMessage()).contains("No se detectó el monto");
        }

        @Test
        void shouldIgnoreResultForAlreadyTerminalReceipt() {
            Receipt receipt = pendingReceipt(1L);
            receipt.setStatus(ReceiptScanStatus.DONE);
            when(receiptRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(receipt));

            service.applyScanResult(new ReceiptScanResultMessage(
                    1L, "DONE", "texto", "rapidocr-ppocrv6", 0.9, 1000L, null));

            verifyNoInteractions(regexParser);
            verify(receiptRepository, never()).save(any());
        }

        @Test
        void shouldIgnoreResultForUnknownReceipt() {
            when(receiptRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

            service.applyScanResult(new ReceiptScanResultMessage(
                    404L, "DONE", "texto", "rapidocr-ppocrv6", 0.9, 1000L, null));

            verify(receiptRepository, never()).save(any());
        }
    }

    @Nested
    class MarkFailedTest {

        @Test
        void shouldMarkPendingReceiptFailed() {
            Receipt receipt = pendingReceipt(1L);
            when(receiptRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(receipt));

            service.markFailed(1L, "worker caído");

            assertThat(receipt.getStatus()).isEqualTo(ReceiptScanStatus.FAILED);
            assertThat(receipt.getErrorMessage()).isEqualTo("worker caído");
        }

        @Test
        void shouldNotOverrideTerminalReceipt() {
            Receipt receipt = pendingReceipt(1L);
            receipt.setStatus(ReceiptScanStatus.DONE);
            when(receiptRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(receipt));

            service.markFailed(1L, "worker caído");

            verify(receiptRepository, never()).save(any());
        }
    }

    @Nested
    class GetScanResultTest {

        @Test
        void shouldReturnSavedResultWithStatus() {
            when(securityService.getCurrentUser()).thenReturn(createTestUser());
            Receipt receipt = pendingReceipt(1L);
            receipt.setStatus(ReceiptScanStatus.DONE);
            receipt.setDetectedAmount(new BigDecimal("45.00"));
            when(receiptRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(receipt));

            ReceiptResponse result = service.getScanResult(1L);

            assertThat(result.getReceiptId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(ReceiptScanStatus.DONE);
            assertThat(result.getDetectedAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
        }

        @Test
        void shouldThrowWhenNotFound() {
            when(securityService.getCurrentUser()).thenReturn(createTestUser());
            when(receiptRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getScanResult(99L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ImproveWithAiTest {

        @Test
        void shouldImproveWhenGeminiConfidenceIsHigher() {
            when(securityService.getCurrentUser()).thenReturn(createTestUser());
            Receipt receipt = pendingReceipt(1L);
            receipt.setStatus(ReceiptScanStatus.DONE);
            receipt.setRawOcrText("TOTAL S/. 45.00");
            receipt.setConfidenceScore(0.5);
            when(receiptRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(receipt));
            when(geminiParser.parse("TOTAL S/. 45.00")).thenReturn(new ReceiptData(
                    new BigDecimal("45.00"), LocalDate.of(2026, 7, 1), "Supermercado ABC",
                    "PEN", PaymentMethod.CASH, TransactionType.SPENT, 0.9, null));
            when(receiptRepository.save(any(Receipt.class))).thenAnswer(i -> i.getArgument(0));

            ReceiptResponse result = service.improveWithAi(1L);

            assertThat(result.getDescription()).isEqualTo("Supermercado ABC");
            assertThat(result.getParserEngineUsed()).isEqualTo("GEMINI");
            assertThat(result.getConfidenceScore()).isEqualTo(0.9);
        }

        @Test
        void shouldThrowWhenReceiptHasNoOcrText() {
            when(securityService.getCurrentUser()).thenReturn(createTestUser());
            Receipt receipt = pendingReceipt(1L);
            when(receiptRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(receipt));

            assertThatThrownBy(() -> service.improveWithAi(1L))
                    .isInstanceOf(OcrProcessingException.class);
        }
    }

    private Receipt pendingReceipt(Long id) {
        return Receipt.builder()
                .id(id)
                .user(createTestUser())
                .status(ReceiptScanStatus.PENDING)
                .build();
    }

    private User createTestUser() {
        User u = new User();
        u.setId(1L);
        return u;
    }
}
