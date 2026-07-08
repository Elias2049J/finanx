package com.elias.finanx.service;

import com.elias.finanx.entity.enums.SubscriptionPlan;

public interface SubscriptionService {

    SubscriptionPlan getActivePlanFor(Long userId);
}
