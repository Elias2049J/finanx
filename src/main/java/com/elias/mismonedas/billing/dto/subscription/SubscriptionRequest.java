package com.elias.mismonedas.billing.dto.subscription;

import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data required to create a new subscription")
public class SubscriptionRequest {

    @NotNull
    @Schema(description = "Id of the subscribing user", example = "42")
    private Long userId;

    @NotNull
    @Schema(description = "Subscription plan", example = "PREMIUM")
    private SubscriptionPlan plan;

    @NotNull
    @Schema(description = "Billing cycle", example = "MONTHLY")
    private BillingCycle billingCycle;

    @NotNull
    @Schema(description = "Amount charged per billing cycle", example = "29.90")
    private BigDecimal amount;

    @NotNull
    @Schema(description = "ISO currency code", example = "PEN")
    private String currency;

    @Schema(description = "Whether the subscription renews automatically", example = "true")
    private Boolean autoRenew;
}
