package com.elias.mismonedas.service.impl;

import com.elias.mismonedas.dto.payment.ReceiptResponse;
import com.elias.mismonedas.entity.Receipt;
import com.elias.mismonedas.entity.User;
import com.elias.mismonedas.exception.OcrBusyException;
import com.elias.mismonedas.port.OcrPort;
import com.elias.mismonedas.port.OcrResult;
import com.elias.mismonedas.port.ReceiptData;
import com.elias.mismonedas.port.ReceiptParserPort;
import com.elias.mismonedas.repository.ReceiptRepository;
import com.elias.mismonedas.repository.UserRepository;
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
    @Mock private UserRepository userRepository;

    private ReceiptScanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReceiptScanServiceImpl(ocrPort, regexParser, geminiParser, receiptRepository, userRepository);
    }

    @Nested
    class ScanReceiptTest {

        @Test
        void shouldScanAndReturnResult() {
            byte[] image = "fake-image-bytes".getBytes();
            when(userRepository.findById(1L)).thenReturn(Optional.of(createTestUser()));
            when(ocrPort.extractText(image)).thenReturn(new OcrResult("TOTAL S/. 45.00", 0.6, 1500L));
            when(regexParser.parse("TOTAL S/. 45.00")).thenReturn(
                    new ReceiptData(new BigDecimal("45.00"), LocalDate.of(2026, 7, 1), "Supermercado", "PEN", 0.6));

            Receipt saved = Receipt.builder()
                    .id(1L).user(createTestUser())
                    .rawOcrText("TOTAL S/. 45.00")
                    .detectedAmount(new BigDecimal("45.00"))
                    .detectedDate(LocalDate.of(2026, 7, 1))
                    .detectedMerchant("Supermercado")
                    .confidenceScore(0.6)
                    .parserEngineUsed("REGEX")
                    .ocrDurationMs(1500L)
                    .build();
            when(receiptRepository.save(any(Receipt.class))).thenReturn(saved);

            ReceiptResponse result = service.scanReceipt(1L, image);

            assertThat(result.getReceiptId()).isEqualTo(1L);
            assertThat(result.getDetectedAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
            assertThat(result.getDetectedMerchant()).isEqualTo("Supermercado");
            assertThat(result.getParserEngineUsed()).isEqualTo("REGEX");
            verify(geminiParser, never()).parse(any());
        }

        @Test
        void shouldFallbackToGeminiWhenRegexConfidenceIsLow() {
            byte[] image = "fake-image-bytes".getBytes();
            when(userRepository.findById(1L)).thenReturn(Optional.of(createTestUser()));
            when(ocrPort.extractText(image)).thenReturn(new OcrResult("texto ambiguo sin monto claro", 0.4, 2000L));
            when(regexParser.parse("texto ambiguo sin monto claro")).thenReturn(
                    new ReceiptData(null, null, null, "PEN", 0.0));
            when(geminiParser.parse("texto ambiguo sin monto claro")).thenReturn(
                    new ReceiptData(new BigDecimal("32.50"), LocalDate.of(2026, 7, 2), "Restaurante", "PEN", 0.85));

            Receipt saved = Receipt.builder()
                    .id(2L).user(createTestUser())
                    .rawOcrText("texto ambiguo sin monto claro")
                    .detectedAmount(new BigDecimal("32.50"))
                    .detectedDate(LocalDate.of(2026, 7, 2))
                    .detectedMerchant("Restaurante")
                    .confidenceScore(0.85)
                    .parserEngineUsed("GEMINI")
                    .ocrDurationMs(2000L)
                    .build();
            when(receiptRepository.save(any(Receipt.class))).thenReturn(saved);

            ReceiptResponse result = service.scanReceipt(1L, image);

            assertThat(result.getParserEngineUsed()).isEqualTo("GEMINI");
            assertThat(result.getDetectedMerchant()).isEqualTo("Restaurante");
            verify(geminiParser).parse(any());
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.scanReceipt(99L, "test".getBytes()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Usuario no encontrado");
        }

        @Test
        void shouldThrowWhenOcrIsBusy() {
            byte[] image = "test".getBytes();
            when(userRepository.findById(1L)).thenReturn(Optional.of(createTestUser()));
            when(ocrPort.extractText(image)).thenThrow(new OcrBusyException("Servicio ocupado"));

            assertThatThrownBy(() -> service.scanReceipt(1L, image))
                    .isInstanceOf(OcrBusyException.class);
        }

        @Test
        void shouldKeepRegexResultWhenGeminiFallbackFails() {
            byte[] image = "test".getBytes();
            when(userRepository.findById(1L)).thenReturn(Optional.of(createTestUser()));
            when(ocrPort.extractText(image)).thenReturn(new OcrResult("TOTAL S/. 15.00", 0.6, 1000L));
            when(regexParser.parse("TOTAL S/. 15.00")).thenReturn(
                    new ReceiptData(new BigDecimal("15.00"), null, null, "PEN", 0.0));
            when(geminiParser.parse("TOTAL S/. 15.00")).thenThrow(new RuntimeException("Gemini error"));

            Receipt saved = Receipt.builder()
                    .id(3L).user(createTestUser()).rawOcrText("TOTAL S/. 15.00")
                    .detectedAmount(new BigDecimal("15.00"))
                    .confidenceScore(0.0)
                    .parserEngineUsed("REGEX")
                    .ocrDurationMs(1000L)
                    .build();
            when(receiptRepository.save(any(Receipt.class))).thenReturn(saved);

            ReceiptResponse result = service.scanReceipt(1L, image);

            assertThat(result.getDetectedAmount()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(result.getParserEngineUsed()).isEqualTo("REGEX");
        }
    }

    @Nested
    class GetScanResultTest {

        @Test
        void shouldReturnSavedResult() {
            Receipt receipt = Receipt.builder()
                    .id(1L).user(createTestUser())
                    .detectedAmount(new BigDecimal("45.00"))
                    .detectedDate(LocalDate.of(2026, 7, 1))
                    .detectedMerchant("Tienda")
                    .confidenceScore(0.6)
                    .parserEngineUsed("REGEX")
                    .ocrDurationMs(1000L)
                    .build();

            when(receiptRepository.findByIdAndUser_Id(1L, 1L)).thenReturn(Optional.of(receipt));

            ReceiptResponse result = service.getScanResult(1L, 1L);

            assertThat(result.getReceiptId()).isEqualTo(1L);
            assertThat(result.getDetectedAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
            assertThat(result.getDetectedMerchant()).isEqualTo("Tienda");
        }

        @Test
        void shouldThrowWhenNotFound() {
            when(receiptRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getScanResult(1L, 99L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private User createTestUser() {
        User u = new User();
        u.setId(1L);
        return u;
    }
}
