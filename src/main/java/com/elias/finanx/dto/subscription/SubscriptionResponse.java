package com.elias.finanx.dto.subscription;

import com.elias.finanx.entity.enums.BillingCycle;
import com.elias.finanx.entity.enums.SubscriptionPlan;
import com.elias.finanx.entity.enums.SubscriptionState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Subscription data returned by the API")
public class SubscriptionResponse {

    @Schema(description = "Unique subscription id", example = "500")
    private Long id;

    @Schema(description = "Id of the subscribing user", example = "42")
    private Long userId;

    @Schema(description = "Subscription plan", example = "PREMIUM")
    private SubscriptionPlan plan;

    @Schema(description = "Billing cycle", example = "MONTHLY")
    private BillingCycle billingCycle;

    @Schema(description = "Current subscription state", example = "ACTIVE")
    private SubscriptionState state;

    @Schema(description = "Amount charged per billing cycle", example = "29.90")
    private BigDecimal amount;

    @Schema(description = "ISO currency code", example = "PEN")
    private String currency;

    @Schema(description = "Whether the subscription renews automatically", example = "true")
    private Boolean autoRenew;

    @Schema(description = "Start of the current billing period", example = "2026-06-07T00:00:00")
    private LocalDateTime currentPeriodStart;

    @Schema(description = "End of the current billing period", example = "2026-07-07T00:00:00")
    private LocalDateTime currentPeriodEnd;

    @Schema(description = "Date and time the subscription was canceled, if applicable", example = "2026-07-01T12:00:00")
    private LocalDateTime canceledAt;

    @Schema(description = "Creation date and time", example = "2026-06-07T00:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Last update date and time", example = "2026-07-01T12:00:00")
    private LocalDateTime updatedAt;
}
