package com.elias.mismonedas.billing.dto.googleplay;

import com.elias.mismonedas.billing.domain.enums.GooglePlayPurchaseState;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionResponse;
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
@Schema(description = "Resultado de verificar una compra de Google Play")
public class GooglePlayVerificationResponse {

    @Schema(description = "Indica si la compra fue verificada y aplicada", example = "true")
    private boolean verified;

    @Schema(description = "Indica si se uso el verificador fake de desarrollo", example = "true")
    private boolean testMode;

    @Schema(description = "Estado normalizado de la compra", example = "ACTIVE")
    private GooglePlayPurchaseState status;

    @Schema(description = "Mensaje informativo para estados pendientes o rechazados")
    private String message;

    @Schema(description = "Product id verificado", example = "premium_monthly_test")
    private String productId;

    @Schema(description = "Base plan id verificado", example = "monthly-test")
    private String basePlanId;

    @Schema(description = "Fecha de expiracion reportada por el verificador")
    private LocalDateTime expiresAt;

    @Schema(description = "Indica si la suscripcion se renueva automaticamente")
    private Boolean autoRenew;

    @Schema(description = "Suscripcion activa resultante, si la compra fue aplicada")
    private SubscriptionResponse subscription;
}
