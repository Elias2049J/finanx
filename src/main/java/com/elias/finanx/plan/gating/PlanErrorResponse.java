package com.elias.finanx.plan.gating;

import com.elias.finanx.entity.enums.SubscriptionPlan;
import io.swagger.v3.oas.annotations.media.Schema;

public record PlanErrorResponse(
    @Schema(description = "Codigo de error estable para el cliente") String code,
    @Schema(description = "Mensaje legible para debugging") String message,
    @Schema(description = "Planes que hubieran permitido la operacion") SubscriptionPlan[] required,
    @Schema(description = "Plan actual del usuario") SubscriptionPlan current
) {}
