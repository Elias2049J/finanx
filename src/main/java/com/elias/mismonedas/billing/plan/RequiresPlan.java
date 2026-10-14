package com.elias.mismonedas.billing.plan;

import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPlan {
    SubscriptionPlan[] value();
}
