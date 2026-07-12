package com.elias.mismonedas.service.impl;

import com.elias.mismonedas.dto.payment.ReceiptResponse;
import com.elias.mismonedas.entity.Image;
import com.elias.mismonedas.entity.Receipt;
import com.elias.mismonedas.entity.User;
import com.elias.mismonedas.entity.enums.PaymentMethod;
import com.elias.mismonedas.entity.enums.TransactionType;
import com.elias.mismonedas.exception.OcrBusyException;
import com.elias.mismonedas.exception.OcrProcessingException;
import com.elias.mismonedas.port.OcrPort;
import com.elias.mismonedas.port.OcrResult;
import com.elias.mismonedas.port.ReceiptData;
import com.elias.mismonedas.port.ReceiptParserPort;
import com.elias.mismonedas.repository.CategoryRepository;
import com.elias.mismonedas.repository.ReceiptRepository;
import com.elias.mismonedas.repository.UserRepository;
import com.elias.mismonedas.service.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock private OcrPort ocrPort;
    @Mock private ReceiptParserPort regexParser;
    @Mock private ReceiptParserPort geminiParser;
    @Mock private ReceiptRepository receiptRepository;
    @Mock private ImageService imageService;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;

    private ReceiptScanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReceiptScanServiceImpl(ocrPort, regexParser, geminiParser, receiptRepository, imageService, userRepository, categoryRepository, 50, 60);
    }

    @Nested
    class ScanReceiptTest {

        @Test
        void shouldScanAndReturnResult() {
            byte[] image = "fake-image-bytes".getBytes();
            when(userRepository.findById(1L)).thenReturn(Optional.of(createTestUser()));
            when(ocrPort.extractText(image)).thenReturn(new OcrResult("TOTAL S/. 45.00", 0.6, 1500L));
            when(regexParser.parse("TOTAL S/. 45.00")).thenReturn(
                    new ReceiptData(new BigDecimal("45.00"), LocalDate.of(2026, 7, 1), "Supermercado", "PEN", PaymentMethod.CASH, TransactionType.SPENT, 0.6, null));

            Receipt saved = Receipt.builder()
                    .id(1L).user(createTestUser())
                    .rawOcrText("TOTAL S/. 45.00")
                    .detectedAmount(new BigDecimal("45.00"))
                    .detectedDate(LocalDate.of(2026, 7, 1))
                    .description("Supermercado")
                    .paymentMethod(PaymentMethod.CASH)
                    .transactionType(TransactionType.SPENT)
                    .currencyGuess("PEN")
                    .confidenceScore(0.6)
                    .parserEngineUsed("REGEX")
                    .ocrDurationMs(1500L)
                    .build();
            when(receiptRepository.save(any(Receipt.class))).thenReturn(saved);

            Image savedImage = Image.builder()
                    .id(1L)
                    .user(createTestUser())
                    .filename("recibo.jpg")
                    .mediaType("image/jpeg")
                    .data(image)
                    .build();
            when(imageService.save(any(User.class), any(), any(), any())).thenReturn(savedImage);

            ReceiptResponse result = service.scanReceipt(1L, image, "recibo.jpg", "image/jpeg");

            assertThat(result.getReceiptId()).isEqualTo(1L);
            assertThat(result.getImageId()).isEqualTo(1L);
            assertThat(result.getDetectedAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
            assertThat(result.getDescription()).isEqualTo("Supermercado");
            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
            assertThat(result.getTransactionType()).isEqualTo(TransactionType.SPENT);
            assertThat(result.getParserEngineUsed()).isEqualTo("REGEX");
            assertThat(result.getRawOcrText()).isEqualTo("TOTAL S/. 45.00");
            assertThat(result.getCurrencyGuess()).isEqualTo("PEN");
            verify(geminiParser, never()).parse(any());
        }

        @Test
        void shouldFallbackToGeminiWhenRegexConfidenceIsLow() {
            byte[] image = "fake-image-bytes".getBytes();
            when(userRepository.findById(1L)).thenReturn(Optional.of(createTestUser()));
            when(ocrPort.extractText(image)).thenReturn(new OcrResult("texto ambiguo sin monto claro", 0.4, 2000L));
            when(regexParser.parse("texto ambiguo sin monto claro")).thenReturn(
                    new ReceiptData(null, null, null, "PEN", null, null, 0.0, null));
            when(geminiParser.parse("texto ambiguo sin monto claro")).thenReturn(
                    new ReceiptData(new BigDecimal("32.50"), LocalDate.of(2026, 7, 2), "Restaurante", "PEN", PaymentMethod.CREDIT_CARD, TransactionType.SPENT, 0.85, null));

            Receipt saved = Receipt.builder()
                    .id(2L).user(createTestUser())
                    .rawOcrText("texto ambiguo sin monto claro")
                    .detectedAmount(new BigDecimal("32.50"))
                    .detectedDate(LocalDate.of(2026, 7, 2))
                    .description("Restaurante")
                    .paymentMethod(PaymentMethod.CREDIT_CARD)
                    .transactionType(TransactionType.SPENT)
                    .currencyGuess("PEN")
                    .confidenceScore(0.85)
                    .parserEngineUsed("GEMINI")
                    .ocrDurationMs(2000L)
                    .build();
            when(receiptRepository.save(any(Receipt.class))).thenReturn(saved);

            Image savedImage = Image.builder()
                    .id(2L)
                    .user(createTestUser())
                    .filename("recibo.png")
                    .mediaType("image/png")
                    .data(image)
                    .build();
            when(imageService.save(any(User.class), any(), any(), any())).thenReturn(savedImage);

            ReceiptResponse result = service.scanReceipt(1L, image, "recibo.png", "image/png");

            assertThat(result.getParserEngineUsed()).isEqualTo("GEMINI");
            assertThat(result.getDescription()).isEqualTo("Restaurante");
            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
            assertThat(result.getRawOcrText()).isEqualTo("texto ambiguo sin monto claro");
            assertThat(result.getCurrencyGuess()).isEqualTo("PEN");
            verify(geminiParser).parse(any());
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.scanReceipt(99L, "test".getBytes(), "test.jpg", "image/jpeg"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Usuario no encontrado");
        }

        @Test
        void shouldThrowWhenOcrIsBusy() {
            byte[] image = "test".getBytes();
            when(userRepository.findById(1L)).thenReturn(Optional.of(createTestUser()));
            when(ocrPort.extractText(image)).thenThrow(new OcrBusyException("Servicio ocupado"));

            assertThatThrownBy(() -> service.scanReceipt(1L, image, "test.jpg", "image/jpeg"))
                    .isInstanceOf(OcrBusyException.class);
        }

        @Test
        void shouldThrowWhenAmountIsNull() {
            byte[] image = "test".getBytes();
            when(userRepository.findById(1L)).thenReturn(Optional.of(createTestUser()));
            when(ocrPort.extractText(image)).thenReturn(new OcrResult("texto sin monto", 0.3, 1000L));
            when(regexParser.parse("texto sin monto")).thenReturn(
                    new ReceiptData(null, null, null, "PEN", null, null, 0.0, null));
            when(geminiParser.parse("texto sin monto")).thenThrow(new RuntimeException("Gemini error"));

            assertThatThrownBy(() -> service.scanReceipt(1L, image, "test.jpg", "image/jpeg"))
                    .isInstanceOf(OcrProcessingException.class)
                    .hasMessageContaining("No se detectó el monto");
        }

        @Test
        void shouldAcceptNullDate() {
            byte[] image = "test".getBytes();
            when(userRepository.findById(1L)).thenReturn(Optional.of(createTestUser()));
            when(ocrPort.extractText(image)).thenReturn(new OcrResult("TOTAL S/. 25.00", 0.6, 1000L));
            when(regexParser.parse("TOTAL S/. 25.00")).thenReturn(
                    new ReceiptData(new BigDecimal("25.00"), null, "Tienda", "PEN", null, null, 0.6, null));

            Receipt saved = Receipt.builder()
                    .id(3L).user(createTestUser())
                    .rawOcrText("TOTAL S/. 25.00")
                    .detectedAmount(new BigDecimal("25.00"))
                    .description("Tienda")
                    .currencyGuess("PEN")
                    .confidenceScore(0.5)
                    .parserEngineUsed("REGEX")
                    .ocrDurationMs(1000L)
                    .build();
            when(receiptRepository.save(any(Receipt.class))).thenReturn(saved);

            ReceiptResponse result = service.scanReceipt(1L, image, null, null);

            assertThat(result.getReceiptId()).isEqualTo(3L);
            assertThat(result.getImageId()).isNull();
            assertThat(result.getDetectedAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
            assertThat(result.getDetectedDate()).isNull();
            assertThat(result.getDescription()).isEqualTo("Tienda");
        }
    }

    @Nested
    class GetScanResultTest {

        @Test
        void shouldReturnSavedResult() {
            Receipt receipt = Receipt.builder()
                    .id(1L).user(createTestUser())
                    .rawOcrText("TOTAL S/. 45.00")
                    .detectedAmount(new BigDecimal("45.00"))
                    .detectedDate(LocalDate.of(2026, 7, 1))
                    .description("Tienda")
                    .paymentMethod(PaymentMethod.CASH)
                    .transactionType(TransactionType.SPENT)
                    .currencyGuess("PEN")
                    .confidenceScore(0.6)
                    .parserEngineUsed("REGEX")
                    .ocrDurationMs(1000L)
                    .build();

            when(receiptRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(receipt));

            ReceiptResponse result = service.getScanResult(1L, 1L);

            assertThat(result.getReceiptId()).isEqualTo(1L);
            assertThat(result.getImageId()).isNull();
            assertThat(result.getDetectedAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
            assertThat(result.getDescription()).isEqualTo("Tienda");
            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
            assertThat(result.getTransactionType()).isEqualTo(TransactionType.SPENT);
            assertThat(result.getRawOcrText()).isEqualTo("TOTAL S/. 45.00");
            assertThat(result.getCurrencyGuess()).isEqualTo("PEN");
        }

        @Test
        void shouldThrowWhenNotFound() {
            when(receiptRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getScanResult(1L, 99L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ImproveWithAiTest {

        @Test
        void shouldImproveWhenGeminiConfidenceIsHigher() {
            Receipt receipt = Receipt.builder()
                    .id(1L).user(createTestUser())
                    .rawOcrText("TOTAL S/. 45.00")
                    .detectedAmount(new BigDecimal("45.00"))
                    .detectedDate(LocalDate.of(2026, 7, 1))
                    .description("Supermercado")
                    .currencyGuess("PEN")
                    .confidenceScore(0.5)
                    .parserEngineUsed("REGEX")
                    .build();
            when(receiptRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(receipt));
            when(geminiParser.parse("TOTAL S/. 45.00")).thenReturn(
                    new ReceiptData(new BigDecimal("45.00"), LocalDate.of(2026, 7, 1), "Supermercado ABC", "PEN", PaymentMethod.CASH, TransactionType.SPENT, 0.9, null));

            Receipt updated = Receipt.builder()
                    .id(1L).user(createTestUser())
                    .rawOcrText("TOTAL S/. 45.00")
                    .detectedAmount(new BigDecimal("45.00"))
                    .detectedDate(LocalDate.of(2026, 7, 1))
                    .description("Supermercado ABC")
                    .paymentMethod(PaymentMethod.CASH)
                    .transactionType(TransactionType.SPENT)
                    .currencyGuess("PEN")
                    .confidenceScore(0.9)
                    .parserEngineUsed("GEMINI")
                    .build();
            when(receiptRepository.save(any(Receipt.class))).thenReturn(updated);

            ReceiptResponse result = service.improveWithAi(1L, 1L);

            assertThat(result.getDescription()).isEqualTo("Supermercado ABC");
            assertThat(result.getParserEngineUsed()).isEqualTo("GEMINI");
            assertThat(result.getConfidenceScore()).isEqualTo(0.9);
        }

        @Test
        void shouldNotImproveWhenGeminiConfidenceIsLower() {
            Receipt receipt = Receipt.builder()
                    .id(1L).user(createTestUser())
                    .rawOcrText("TOTAL S/. 45.00")
                    .detectedAmount(new BigDecimal("45.00"))
                    .detectedDate(LocalDate.of(2026, 7, 1))
                    .description("Supermercado")
                    .currencyGuess("PEN")
                    .confidenceScore(0.85)
                    .parserEngineUsed("REGEX")
                    .build();
            when(receiptRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(receipt));
            when(geminiParser.parse("TOTAL S/. 45.00")).thenReturn(
                    new ReceiptData(new BigDecimal("44.00"), LocalDate.of(2026, 7, 1), "Supermercado", "PEN", null, null, 0.6, null));

            ReceiptResponse result = service.improveWithAi(1L, 1L);

            assertThat(result.getDetectedAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
            assertThat(result.getParserEngineUsed()).isEqualTo("REGEX");
            assertThat(result.getConfidenceScore()).isEqualTo(0.85);
            verify(receiptRepository, never()).save(any());
        }

        @Test
        void shouldThrowWhenReceiptNotFound() {
            when(receiptRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.improveWithAi(99L, 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private User createTestUser() {
        User u = new User();
        u.setId(1L);
        return u;
    }
}
