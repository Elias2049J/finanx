package com.elias.mismonedas.repository;

import com.elias.mismonedas.entity.Subscription;
import com.elias.mismonedas.entity.enums.SubscriptionState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByUser_Id(Long userId);

    Optional<Subscription> findFirstByUser_IdAndStateOrderByCreatedAtDesc(Long userId, SubscriptionState state);

    List<Subscription> findAllByStateAndCurrentPeriodEndBefore(SubscriptionState state, OffsetDateTime moment);
}
