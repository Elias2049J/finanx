package com.elias.mismonedas.billing.dto.googleplay;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos enviados por la app para verificar una compra de Google Play")
public class GooglePlayVerificationRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Application id de la app en Play", example = "com.elias.mismonedas")
    private String packageName;

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Product id configurado en Play Console", example = "premium_monthly_test")
    private String productId;

    @Size(max = 255)
    @Schema(description = "Base plan id de la suscripcion", example = "monthly-test")
    private String basePlanId;

    @NotBlank
    @Size(max = 2048)
    @Schema(description = "Token de compra devuelto por Google Play Billing")
    private String purchaseToken;
}
