package com.elias.mismonedas.scheduling.dto.schedule;

import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.scheduling.domain.enums.ExecutionMode;
import com.elias.mismonedas.scheduling.domain.enums.OccurrenceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ocurrencia de un recordatorio proyectada dentro de un rango de fechas")
public class OccurrenceResponse {

    @Schema(
            description = "Identificador de la programacion que origina la ocurrencia",
            example = "12"
    )
    private Long scheduleId;

    @Schema(
            description = "Tipo de programacion que origina la ocurrencia",
            example = "TRANSACTION",
            allowableValues = {"TRANSACTION", "BUDGET"}
    )
    private String kind;

    @Schema(
            description = "Fecha y hora de la ocurrencia en la zona horaria del usuario",
            example = "2026-10-15T09:00:00"
    )
    private LocalDateTime occurrenceAt;

    @Schema(
            description = "PENDING si la ocurrencia espera confirmacion manual; UPCOMING si es una proyeccion futura",
            example = "PENDING"
    )
    private OccurrenceStatus status;

    @Schema(
            description = "Modo de ejecucion configurado en el recordatorio",
            example = "MANUAL"
    )
    private ExecutionMode executionMode;

    @Schema(
            description = "Clave de la ocurrencia pendiente; solo presente cuando status es PENDING",
            example = "schedule:12:1792054800000"
    )
    private String occurrenceKey;

    @Schema(
            description = "Identificador de la categoria",
            example = "3"
    )
    private Long categoryId;

    @Schema(
            description = "Nombre de la categoria",
            example = "Servicios"
    )
    private String categoryName;

    @Schema(
            description = "Descripcion del recordatorio o del motivo asociado",
            example = "Pago de internet"
    )
    private String description;

    @Schema(
            description = "Monto de la ocurrencia",
            example = "120.00"
    )
    private BigDecimal amount;

    @Schema(
            description = "Moneda de la ocurrencia",
            example = "PEN"
    )
    private String currencyCode;

    @Schema(
            description = "Tipo de movimiento; solo aplica a ocurrencias de transacciones",
            example = "EXPENSE"
    )
    private TransactionType transactionType;
}
