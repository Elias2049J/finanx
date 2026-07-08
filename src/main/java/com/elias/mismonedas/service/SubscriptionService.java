package com.elias.mismonedas.service;

import com.elias.mismonedas.entity.enums.SubscriptionPlan;

public interface SubscriptionService {

    SubscriptionPlan getActivePlanFor(Long userId);
}
