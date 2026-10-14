package com.elias.mismonedas.billing.domain.enums;

import java.util.Map;
import java.util.Set;

/**
 * State machine for {@link com.elias.mismonedas.billing.domain.entity.Payment}.
 *
 * PENDING    -> PROCESSING, COMPLETED, FAILED
 * PROCESSING -> COMPLETED, FAILED
 * COMPLETED  -> REFUNDED, DISPUTED
 * FAILED     -> PENDING (retry)
 * REFUNDED   -> (final)
 * DISPUTED   -> REFUNDED
 */
public enum PaymentState {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUNDED,
    DISPUTED;

    private static final Map<PaymentState, Set<PaymentState>> TRANSITIONS = Map.of(
            PENDING, Set.of(PROCESSING, COMPLETED, FAILED),
            PROCESSING, Set.of(COMPLETED, FAILED),
            COMPLETED, Set.of(REFUNDED, DISPUTED),
            FAILED, Set.of(PENDING),
            REFUNDED, Set.of(),
            DISPUTED, Set.of(REFUNDED)
    );

    public boolean canTransitionTo(PaymentState target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isFinal() {
        return this == REFUNDED;
    }
}
