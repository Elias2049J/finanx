package com.elias.mismonedas.billing.service;

import com.elias.mismonedas.billing.dto.subscription.SubscriptionRequest;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionResponse;
import com.elias.mismonedas.billing.domain.enums.SubscriptionState;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Business operations for subscriptions and their lifecycle.
 */
public interface BillingService {

    SubscriptionResponse subscribe(SubscriptionRequest request);

    /** System/internal: creates and activates a subscription for the user in the request (no auth context). */
    SubscriptionResponse subscribeAndActivate(SubscriptionRequest request);

    SubscriptionResponse findById(Long id);

    List<SubscriptionResponse> findAllByUser(Long userId);

    PageResponse<SubscriptionResponse> list(SubscriptionState state, Pageable pageable);

    /** Returns the active subscription of the authenticated user, or 404 when none is active. */
    SubscriptionResponse getCurrentSubscription();

    SubscriptionResponse activate(Long subscriptionId);

    SubscriptionResponse cancel(Long subscriptionId);

    SubscriptionResponse renew(Long subscriptionId);

    /** Scans active subscriptions past their current period end and marks them PAST_DUE/EXPIRED. */
    void checkExpirations();
}
