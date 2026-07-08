package com.elias.finanx.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment receipt generated automatically when a payment is completed")
public class Receipt {

    @Schema(description = "Payment id", example = "9001")
    private Long paymentId;

    @Schema(description = "Receipt number (series + sequential)", example = "RCP-2026-000042")
    private String number;

    @Schema(description = "Date and time the receipt was issued", example = "2026-07-01T12:00:00")
    private LocalDateTime issuedAt;
}
