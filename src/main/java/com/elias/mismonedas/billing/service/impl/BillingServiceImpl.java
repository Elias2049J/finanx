package com.elias.mismonedas.billing.service.impl;

import com.elias.mismonedas.billing.domain.entity.Subscription;
import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.SubscriptionState;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionRequest;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionResponse;
import com.elias.mismonedas.billing.mapper.SubscriptionMapper;
import com.elias.mismonedas.billing.repository.SubscriptionRepository;
import com.elias.mismonedas.billing.repository.SubscriptionSpecifications;
import com.elias.mismonedas.billing.service.BillingService;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.elias.mismonedas.shared.exception.SubscriptionNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final SecurityService securityService;

    @Override
    @Transactional
    public SubscriptionResponse subscribe(SubscriptionRequest request) {
        User user = securityService.getCurrentUser();
        Subscription subscription = buildPending(request, user);
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription created id={} userId={} plan={}", saved.getId(), user.getId(), saved.getPlan());
        return subscriptionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void subscribeAndActivate(SubscriptionRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + request.getUserId()));
        Subscription subscription = buildPending(request, user);
        activate(subscription);
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription created+activated id={} userId={} plan={}", saved.getId(), user.getId(), saved.getPlan());
        subscriptionMapper.toResponse(saved);
    }

    private Subscription buildPending(SubscriptionRequest request, User user) {
        Subscription subscription = subscriptionMapper.toEntity(request);
        subscription.setUser(user);
        subscription.setState(SubscriptionState.PENDING);
        return subscription;
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse findById(Long id) {
        Subscription subscription = getOwned(id);
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
    @Transactional(readOnly = true)
    public PageResponse<SubscriptionResponse> list(SubscriptionState state, Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Subscription> page = subscriptionRepository.findAll(
                SubscriptionSpecifications.list(userId, state), stablePage);
        return PageResponseFactory.from(page, subscriptionMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrentSubscription() {
        Long userId = securityService.getCurrentUser().getId();
        return subscriptionRepository
                .findFirstByUser_IdAndStateOrderByCreatedAtDesc(userId, SubscriptionState.ACTIVE)
                .map(subscriptionMapper::toResponse)
                .orElseThrow(() -> new SubscriptionNotFoundException(
                        "No active subscription for current user"));
    }

    @Override
    @Transactional
    public SubscriptionResponse activate(Long subscriptionId) {
        Subscription subscription = getOwned(subscriptionId);
        activate(subscription);
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Subscription activated id={}", saved.getId());
        return subscriptionMapper.toResponse(saved);
    }

    private void activate(Subscription subscription) {
        OffsetDateTime now = OffsetDateTime.now();
        subscription.transitionTo(SubscriptionState.ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(nextPeriodEnd(now, subscription.getBillingCycle()));
    }

    @Override
    @Transactional
    public SubscriptionResponse cancel(Long subscriptionId) {
        Subscription subscription = getOwned(subscriptionId);
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
        Subscription subscription = getOwned(subscriptionId);
        OffsetDateTime now = OffsetDateTime.now();

        // C2: doble llamada no debe ser doble periodo. Si el periodo actual sigue vigente,
        // la renovacion es un no-op: no se cobra un periodo y se entregan dos.
        if (subscription.getCurrentPeriodEnd() != null && now.isBefore(subscription.getCurrentPeriodEnd())) {
            log.info("Subscription id={} renewed no-op: current period still open until {}",
                    subscription.getId(), subscription.getCurrentPeriodEnd());
            return subscriptionMapper.toResponse(subscription);
        }

        OffsetDateTime newStart = subscription.getCurrentPeriodEnd() != null
                ? subscription.getCurrentPeriodEnd()
                : now;

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

    private Subscription getOwned(Long id) {
        Long userId = securityService.getCurrentUser().getId();
        return subscriptionRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new SubscriptionNotFoundException(id));
    }
}
