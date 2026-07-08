package com.elias.mismonedas.entity.enums;

import java.util.Map;
import java.util.Set;

/**
 * State machine for {@link com.elias.mismonedas.entity.Subscription}.
 *
 * PENDING   -> ACTIVE, CANCELED
 * ACTIVE    -> PAST_DUE, CANCELED, EXPIRED
 * PAST_DUE  -> ACTIVE, CANCELED, EXPIRED
 * CANCELED  -> (final)
 * EXPIRED   -> (final)
 */
public enum SubscriptionState {
    PENDING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    EXPIRED;

    private static final Map<SubscriptionState, Set<SubscriptionState>> TRANSITIONS = Map.of(
            PENDING, Set.of(ACTIVE, CANCELED),
            ACTIVE, Set.of(PAST_DUE, CANCELED, EXPIRED),
            PAST_DUE, Set.of(ACTIVE, CANCELED, EXPIRED),
            CANCELED, Set.of(),
            EXPIRED, Set.of()
    );

    public boolean canTransitionTo(SubscriptionState target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isActive() {
        return this == ACTIVE;
    }
}
