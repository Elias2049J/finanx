package com.elias.mismonedas.plan.gating;

import com.elias.mismonedas.entity.enums.SubscriptionPlan;
import lombok.Getter;

import java.util.Arrays;

@Getter
public class PlanRequiredException extends RuntimeException {

    private final SubscriptionPlan[] required;
    private final SubscriptionPlan current;

    public PlanRequiredException(SubscriptionPlan[] required, SubscriptionPlan current) {
        super("Requiere plan " + Arrays.toString(required) + ", actual: " + current);
        this.required = required;
        this.current = current;
    }
}
