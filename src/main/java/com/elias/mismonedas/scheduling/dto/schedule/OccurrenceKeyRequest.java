package com.elias.mismonedas.scheduling.dto.schedule;

import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Clave de la ocurrencia pendiente que se desea confirmar u omitir")
public class OccurrenceKeyRequest {

    public OccurrenceKeyRequest(String occurrenceKey) {
        this.occurrenceKey = occurrenceKey;
    }

    @NotBlank
    @Schema(
            description = "Clave de la ocurrencia pendiente devuelta al listar el recordatorio",
            example = "schedule:12:1761700000000",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String occurrenceKey;

    @Positive
    @Schema(description = "Monto con el que se registrara el movimiento; si se omite se usa el del recordatorio", example = "125.50")
    private BigDecimal amount;

    @Schema(description = "Billetera donde se registrara el movimiento; si se omite se usa la del recordatorio", example = "3")
    private Long walletId;

    @Schema(description = "Categoria del movimiento; si se omite se usa la del recordatorio", example = "101")
    private Long categoryId;

    @Schema(description = "Motivo del movimiento; si se omite se usa el del recordatorio", example = "15")
    private Long reasonId;

    @Schema(description = "Descripcion libre del movimiento", example = "Renta de octubre")
    private String description;

    @Schema(description = "Metodo de pago; si se omite se usa el del recordatorio", example = "CASH")
    private PaymentMethod paymentMethod;
}
