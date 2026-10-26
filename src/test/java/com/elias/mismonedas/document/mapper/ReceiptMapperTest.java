package com.elias.mismonedas.document.mapper;

import com.elias.mismonedas.document.domain.entity.Image;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.domain.enums.ReceiptScanStatus;
import com.elias.mismonedas.document.dto.ReceiptResponse;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el mapeo real de {@link ReceiptMapper} (implementación generada por
 * MapStruct), no un mock.
 *
 * Importante: el campo PK de {@link Receipt} se llama {@code id}, mientras que
 * el DTO lo expone como {@code receiptId}. Sin el {@code @Mapping} explícito,
 * MapStruct deja {@code receiptId} a null y el cliente Android nunca puede
 * iniciar el sondeo del OCR (se queda en PENDING para siempre).
 */
class ReceiptMapperTest {

    private final ReceiptMapper mapper = Mappers.getMapper(ReceiptMapper.class);

    @Test
    void shouldMapIdToReceiptIdAndImageId() {
        Image image = Image.builder().id(7L).build();
        Receipt receipt = Receipt.builder()
                .id(42L)
                .image(image)
                .status(ReceiptScanStatus.DONE)
                .detectedAmount(new BigDecimal("3000.00"))
                .detectedDate(LocalDate.of(2026, 8, 14))
                .description("MUNDO DIGITAL")
                .build();

        ReceiptResponse response = mapper.toResponse(receipt);

        assertThat(response.getReceiptId()).isEqualTo(42L);
        assertThat(response.getImageId()).isEqualTo(7L);
        assertThat(response.getStatus()).isEqualTo(ReceiptScanStatus.DONE);
        assertThat(response.getDescription()).isEqualTo("MUNDO DIGITAL");
    }

    @Test
    void shouldDeriveImprovedFromGeminiEngineAndExposeTimestamp() {
        OffsetDateTime improvedAt = OffsetDateTime.parse("2026-10-26T10:15:30-05:00");
        Receipt receipt = Receipt.builder()
                .id(8L)
                .status(ReceiptScanStatus.DONE)
                .confidenceScore(0.92)
                .parserEngineUsed("GEMINI")
                .aiImprovedAt(improvedAt)
                .build();

        ReceiptResponse response = mapper.toResponse(receipt);

        assertThat(response.isImproved()).isTrue();
        assertThat(response.getAiImprovedAt()).isEqualTo(improvedAt);
        assertThat(response.isRequiresReview()).isFalse();
    }

    @Test
    void shouldNotMarkImprovedWhenRegexWasUsed() {
        Receipt receipt = Receipt.builder()
                .id(9L)
                .status(ReceiptScanStatus.DONE)
                .confidenceScore(0.92)
                .parserEngineUsed("REGEX")
                .build();

        ReceiptResponse response = mapper.toResponse(receipt);

        assertThat(response.isImproved()).isFalse();
        assertThat(response.getAiImprovedAt()).isNull();
    }

    @Test
    void shouldRequireReviewForDoneUnlinkedLowConfidenceReceipt() {
        Receipt receipt = Receipt.builder()
                .id(10L)
                .status(ReceiptScanStatus.DONE)
                .confidenceScore(0.69)
                .build();

        ReceiptResponse response = mapper.toResponse(receipt);

        assertThat(response.isRequiresReview()).isTrue();
    }

    @Test
    void shouldNotRequireReviewAtThreshold() {
        Receipt receipt = Receipt.builder()
                .id(11L)
                .status(ReceiptScanStatus.DONE)
                .confidenceScore(0.70)
                .build();

        ReceiptResponse response = mapper.toResponse(receipt);

        assertThat(response.isRequiresReview()).isFalse();
    }

    @Test
    void shouldNotRequireReviewWhenReceiptIsLinkedToTransaction() {
        Receipt receipt = Receipt.builder()
                .id(12L)
                .status(ReceiptScanStatus.DONE)
                .confidenceScore(0.40)
                .transaction(new Transaction())
                .build();

        ReceiptResponse response = mapper.toResponse(receipt);

        assertThat(response.isRequiresReview()).isFalse();
    }

    @Test
    void shouldNotRequireReviewWhileScanIsNotDone() {
        Receipt receipt = Receipt.builder()
                .id(13L)
                .status(ReceiptScanStatus.PENDING)
                .confidenceScore(0.10)
                .build();

        ReceiptResponse response = mapper.toResponse(receipt);

        assertThat(response.isRequiresReview()).isFalse();
    }

    @Test
    void shouldRequireReviewWhenDoneReceiptHasNoConfidence() {
        Receipt receipt = Receipt.builder()
                .id(14L)
                .status(ReceiptScanStatus.DONE)
                .build();

        ReceiptResponse response = mapper.toResponse(receipt);

        assertThat(response.isRequiresReview()).isTrue();
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void shouldMapReceiptIdAsNullWhenIdIsNull() {
        Receipt receipt = Receipt.builder().description("sin id").build();

        ReceiptResponse response = mapper.toResponse(receipt);

        assertThat(response.getReceiptId()).isNull();
        assertThat(response.getImageId()).isNull();
        assertThat(response.getDescription()).isEqualTo("sin id");
    }
}
