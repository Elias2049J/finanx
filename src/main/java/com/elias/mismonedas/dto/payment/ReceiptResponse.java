package com.elias.mismonedas.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@Schema(description = "Resultado del escaneo OCR de un recibo")
public class ReceiptResponse {

    @Schema(description = "Identificador del recibo escaneado", example = "1")
    private Long receiptId;

    @Schema(description = "Monto total detectado", example = "45.00")
    private BigDecimal detectedAmount;

    @Schema(description = "Fecha detectada en el recibo", example = "2026-07-01")
    private LocalDate detectedDate;

    @Schema(description = "Nombre del comercio detectado", example = "Supermercado ABC")
    private String detectedMerchant;

    @Schema(description = "Moneda detectada (no resuelta contra Wallet)", example = "PEN")
    private String currencyGuess;

    @Schema(description = "Nivel de confianza del parseo", example = "0.85")
    private Double confidenceScore;

    @Schema(description = "Motor usado para el parseo (REGEX o GEMINI)", example = "REGEX")
    private String parserEngineUsed;
}
