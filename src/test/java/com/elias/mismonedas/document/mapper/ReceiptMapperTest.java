package com.elias.mismonedas.document.mapper;

import com.elias.mismonedas.document.domain.entity.Image;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.domain.enums.ReceiptScanStatus;
import com.elias.mismonedas.document.dto.ReceiptResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

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
