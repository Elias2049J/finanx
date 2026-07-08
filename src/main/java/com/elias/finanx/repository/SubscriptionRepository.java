package com.elias.finanx.repository;

import com.elias.finanx.entity.Subscription;
import com.elias.finanx.entity.enums.SubscriptionState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByUserId(Long userId);

    Optional<Subscription> findFirstByUserIdAndStateOrderByCreatedAtDesc(Long userId, SubscriptionState state);

    List<Subscription> findAllByStateAndCurrentPeriodEndBefore(SubscriptionState state, OffsetDateTime moment);
}
