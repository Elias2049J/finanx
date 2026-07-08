package com.elias.finanx.dto.payment;

import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data required to start a payment attempt")
public class PaymentRequest {

    @NotNull
    @Schema(description = "Id of the paying user", example = "42")
    private Long userId;

    @Schema(description = "Id of the subscription this payment belongs to, if any", example = "500")
    private Long subscriptionId;

    @NotNull
    @Schema(description = "Amount to charge", example = "29.90")
    private BigDecimal amount;

    @NotNull
    @Schema(description = "ISO currency code", example = "PEN")
    private String currency;

    @NotNull
    @Schema(description = "Payment provider used for this attempt", example = "MERCADO_PAGO")
    private PaymentProvider provider;

    @Schema(description = "Payment method used")
    private PaymentMethod method;

    @Schema(description = "Client-supplied idempotency key, generated automatically when absent", example = "3f2f1e2a-9c3b-4b7a-9e0b-6f6f2a1b9c00")
    private String idempotencyKey;

    @Schema(description = "Free-form metadata for this payment")
    private Map<String, Object> metadata;
}
