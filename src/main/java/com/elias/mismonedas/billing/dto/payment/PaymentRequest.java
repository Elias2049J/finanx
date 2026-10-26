package com.elias.mismonedas.billing.dto.payment;

import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "amount must have at most 12 integer digits and 2 decimals")
    @Schema(description = "Amount to charge", example = "29.90")
    private BigDecimal amount;

    @NotNull
    @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a three-letter ISO-4217 code")
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
