package com.elias.mismonedas.plan.gating;

import com.elias.mismonedas.entity.enums.SubscriptionPlan;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPlan {
    SubscriptionPlan[] value();
}
