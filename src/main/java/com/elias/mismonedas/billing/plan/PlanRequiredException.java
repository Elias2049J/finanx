package com.elias.mismonedas.billing.plan;

import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
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
