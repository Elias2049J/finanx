package com.elias.mismonedas.billing.service;

import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;

public interface SubscriptionService {

    SubscriptionPlan getActivePlanFor(Long userId);
}
