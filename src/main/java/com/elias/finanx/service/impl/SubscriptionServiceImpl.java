package com.elias.finanx.service.impl;

import com.elias.finanx.entity.Subscription;
import com.elias.finanx.entity.enums.SubscriptionPlan;
import com.elias.finanx.entity.enums.SubscriptionState;
import com.elias.finanx.repository.SubscriptionRepository;
import com.elias.finanx.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    @Override
    public SubscriptionPlan getActivePlanFor(Long userId) {
        Optional<Subscription> active = subscriptionRepository
                .findFirstByUser_IdAndStateOrderByCreatedAtDesc(userId, SubscriptionState.ACTIVE);

        if (active.isPresent()) {
            SubscriptionPlan plan = active.get().getPlan();
            log.debug("Plan activo para userId={}: {}", userId, plan);
            return plan;
        }

        log.debug("Sin suscripcion activa para userId={}, retornando FREE", userId);
        return SubscriptionPlan.FREE;
    }
}
