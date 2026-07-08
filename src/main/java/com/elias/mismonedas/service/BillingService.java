package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.subscription.SubscriptionRequest;
import com.elias.mismonedas.dto.subscription.SubscriptionResponse;

import java.util.List;

/**
 * Business operations for subscriptions and their lifecycle.
 */
public interface BillingService {

    SubscriptionResponse subscribe(SubscriptionRequest request);

    SubscriptionResponse findById(Long id);

    List<SubscriptionResponse> findAllByUser(Long userId);

    SubscriptionResponse activate(Long subscriptionId);

    SubscriptionResponse cancel(Long subscriptionId);

    SubscriptionResponse renew(Long subscriptionId);

    /** Scans active subscriptions past their current period end and marks them PAST_DUE/EXPIRED. */
    void checkExpirations();
}
