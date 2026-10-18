package com.elias.mismonedas.billing.dto.payment;

import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payment data returned by the API")
public class PaymentResponse {

    @Schema(description = "Unique payment id", example = "9001")
    private Long id;

    @Schema(description = "Id of the paying user", example = "42")
    private Long userId;

    @Schema(description = "Id of the linked subscription, if any", example = "500")
    private Long subscriptionId;

    @Schema(description = "Charged amount", example = "29.90")
    private BigDecimal amount;

    @Schema(description = "ISO currency code", example = "PEN")
    private String currency;

    @Schema(description = "Payment provider used", example = "MERCADO_PAGO")
    private PaymentProvider provider;

    @Schema(description = "Payment method used")
    private PaymentMethod method;

    @Schema(description = "Current payment state", example = "COMPLETED")
    private PaymentState state;

    @Schema(description = "Reference assigned by the provider", example = "MP-8823991")
    private String externalReference;

    @Schema(description = "Reason the payment failed, if applicable", example = "Insufficient funds")
    private String failureReason;

    @Schema(description = "Date and time the payment was confirmed", example = "2026-07-01T12:00:00")
    private LocalDateTime paidAt;

    @Schema(description = "Creation date and time", example = "2026-07-01T11:58:00")
    private LocalDateTime createdAt;

    @Schema(description = "Last update date and time", example = "2026-07-01T12:00:00")
    private LocalDateTime updatedAt;
}
