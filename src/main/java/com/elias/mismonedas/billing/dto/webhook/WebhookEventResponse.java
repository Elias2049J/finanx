package com.elias.mismonedas.billing.dto.webhook;

import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.domain.enums.WebhookEventState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Record of an inbound webhook notification, for auditing purposes")
public class WebhookEventResponse {

    @Schema(description = "Unique webhook event id", example = "12000")
    private Long id;

    @Schema(description = "Payment provider that sent the notification", example = "MERCADO_PAGO")
    private PaymentProvider provider;

    @Schema(description = "Event type reported by the provider", example = "payment.completed")
    private String eventType;

    @Schema(description = "Unique event id assigned by the provider", example = "evt_8823991")
    private String externalEventId;

    @Schema(description = "Processing state of the webhook event", implementation = WebhookEventState.class, example = "PROCESSED")
    private WebhookEventState state;

    @Schema(description = "Error message captured if processing failed", example = "Payment not found for reference MP-8823991")
    private String errorMessage;

    @Schema(description = "Date and time the event was received", example = "2026-07-01T12:00:00")
    private LocalDateTime receivedAt;

    @Schema(description = "Date and time the event finished processing", example = "2026-07-01T12:00:01")
    private LocalDateTime processedAt;
}
