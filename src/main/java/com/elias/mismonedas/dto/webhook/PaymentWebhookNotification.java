package com.elias.mismonedas.dto.webhook;

import com.elias.mismonedas.entity.enums.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

/**
 * Normalized representation of a provider webhook payload, produced by a
 * {@link com.elias.mismonedas.port.PaymentPort} adapter. Internal use only, not a
 * public request DTO: providers send their own raw payload, not this shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Normalized webhook notification produced by a PaymentPort adapter")
public class PaymentWebhookNotification {

    @Schema(description = "Payment provider that sent the notification")
    private PaymentProvider provider;

    @Schema(description = "Unique event id assigned by the provider, used for idempotency")
    private String externalEventId;

    @Schema(description = "Reference of the payment/transaction on the provider side")
    private String externalReference;

    @Schema(description = "Event type reported by the provider", example = "payment.completed")
    private String eventType;

    @Schema(description = "Amount reported by the provider")
    private BigDecimal amount;

    @Schema(description = "Currency reported by the provider", example = "PEN")
    private String currency;

    @Schema(description = "Normalized status reported by the provider", example = "approved")
    private String status;
}
