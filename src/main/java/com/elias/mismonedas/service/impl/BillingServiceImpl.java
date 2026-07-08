package com.elias.mismonedas.service.impl;

import com.elias.mismonedas.dto.subscription.SubscriptionRequest;
import com.elias.mismonedas.dto.subscription.SubscriptionResponse;
import com.elias.mismonedas.entity.Subscription;
import com.elias.mismonedas.entity.User;
import com.elias.mismonedas.entity.enums.BillingCycle;
import com.elias.mismonedas.entity.enums.SubscriptionState;
import com.elias.mismonedas.exception.SubscriptionNotFoundException;
import com.elias.mismonedas.mapper.SubscriptionMapper;
import com.elias.mismonedas.repository.SubscriptionRepository;
import com.elias.mismonedas.repository.UserRepository;
import com.elias.mismonedas.service.BillingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    @Transactional
    public SubscriptionResponse subscribe(SubscriptionRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + request.getUserId()));

        Subscription subscription = subscriptionMapper.toEntity(request);
        subscription.setUser(user);
        subscription.setState(SubscriptionState.PENDING);

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription created id={} userId={} plan={}", saved.getId(), user.getId(), saved.getPlan());
        return subscriptionMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse findById(Long id) {
        Subscription subscription = getSubscriptionOrThrow(id);
        return subscriptionMapper.toResponse(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> findAllByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + userId));
        return subscriptionRepository.findAllByUser_Id(userId).stream()
                .map(subscriptionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SubscriptionResponse activate(Long subscriptionId) {
        Subscription subscription = getSubscriptionOrThrow(subscriptionId);
        OffsetDateTime now = OffsetDateTime.now();

        subscription.transitionTo(SubscriptionState.ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(nextPeriodEnd(now, subscription.getBillingCycle()));

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription activated id={}", saved.getId());
        return subscriptionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SubscriptionResponse cancel(Long subscriptionId) {
        Subscription subscription = getSubscriptionOrThrow(subscriptionId);
        subscription.transitionTo(SubscriptionState.CANCELED);
        subscription.setCanceledAt(OffsetDateTime.now());
        subscription.setAutoRenew(false);

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription canceled id={}", saved.getId());
        return subscriptionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SubscriptionResponse renew(Long subscriptionId) {
        Subscription subscription = getSubscriptionOrThrow(subscriptionId);
        OffsetDateTime newStart = subscription.getCurrentPeriodEnd() != null
                ? subscription.getCurrentPeriodEnd()
                : OffsetDateTime.now();

        subscription.setCurrentPeriodStart(newStart);
        subscription.setCurrentPeriodEnd(nextPeriodEnd(newStart, subscription.getBillingCycle()));
        if (subscription.getState() != SubscriptionState.ACTIVE) {
            subscription.transitionTo(SubscriptionState.ACTIVE);
        }

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription renewed id={} newPeriodEnd={}", saved.getId(), saved.getCurrentPeriodEnd());
        return subscriptionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void checkExpirations() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Subscription> expiring = subscriptionRepository
                .findAllByStateAndCurrentPeriodEndBefore(SubscriptionState.ACTIVE, now);

        for (Subscription subscription : expiring) {
            SubscriptionState target = subscription.getAutoRenew() ? SubscriptionState.PAST_DUE : SubscriptionState.EXPIRED;
            subscription.transitionTo(target);
            subscriptionRepository.save(subscription);
            log.info("Subscription id={} moved to {}", subscription.getId(), target);
        }
    }

    private OffsetDateTime nextPeriodEnd(OffsetDateTime start, BillingCycle cycle) {
        return cycle == BillingCycle.ANNUAL ? start.plusYears(1) : start.plusMonths(1);
    }

    private Subscription getSubscriptionOrThrow(Long id) {
        return subscriptionRepository.findById(id).orElseThrow(() -> new SubscriptionNotFoundException(id));
    }
}
