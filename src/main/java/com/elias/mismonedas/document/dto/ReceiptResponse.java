package com.elias.mismonedas.document.dto;

import com.elias.mismonedas.document.domain.enums.ReceiptScanStatus;
import com.elias.mismonedas.ledger.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
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

    @Schema(description = "Estado del procesamiento OCR", example = "PENDING")
    private ReceiptScanStatus status;

    @Schema(description = "Mensaje de error si el escaneo falló (nullable)", example = "No se detectó el monto")
    private String errorMessage;

    @Schema(description = "ID de la imagen asociada (nullable si no tiene imagen)", example = "1")
    private Long imageId;

    @Schema(description = "Monto total detectado", example = "45.00")
    private BigDecimal detectedAmount;

    @Schema(description = "Fecha detectada en el recibo (nullable)", example = "2026-07-01")
    private LocalDate detectedDate;

    @Schema(description = "Descripción/nombre del comercio detectado", example = "Supermercado ABC")
    private String description;

    @Schema(description = "Método de pago detectado", example = "CASH")
    private PaymentMethod paymentMethod;

    @Schema(description = "Tipo de transacción detectado", example = "SPENT")
    private TransactionType transactionType;

    @Schema(description = "ID de la categoría detectada (nullable)", example = "3")
    private Long detectedCategoryId;

    @Schema(description = "Texto OCR crudo extraído de la imagen", example = "BOLETA DE VENTA\nTOTAL S/. 45.00")
    private String rawOcrText;

    @Schema(description = "Moneda detectada (no resuelta contra Wallet)", example = "PEN")
    private String currencyGuess;

    @Schema(description = "Nivel de confianza del parseo", example = "0.85")
    private Double confidenceScore;

    @Schema(description = "Motor usado para el parseo (REGEX o GEMINI)", example = "REGEX")
    private String parserEngineUsed;
}
