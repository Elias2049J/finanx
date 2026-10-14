package com.elias.mismonedas.planning.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.planning.domain.enums.SavingGoalState;
import com.elias.mismonedas.planning.domain.event.SavingGoalCompletedEvent;
import com.elias.mismonedas.planning.dto.saving.SavingGoalRequest;
import com.elias.mismonedas.planning.dto.saving.SavingGoalResponse;
import com.elias.mismonedas.planning.mapper.SavingGoalMapper;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import com.elias.mismonedas.planning.service.SavingGoalService;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
@Slf4j
public class SavingGoalServiceImpl implements SavingGoalService {
    private final SavingGoalRepository sgRepository;
    private final SavingGoalMapper sgMapper;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrencyConverter currencyConverter;

    @Override
    @Transactional
    public SavingGoalResponse create(SavingGoalRequest request) {
        SavingGoal sg = sgMapper.toEntity(request);

        User user = userRepository.getReferenceById(request.getUserId());
        sg.setUser(user);

        sg.setCurrencyCode(user.getPreferences().getCurrencyCode());

        ZoneId userZone = user.getTimeZone().toZoneId();
        sg.setDeadline(request.getDeadline().atStartOfDay().atZone(userZone).toOffsetDateTime());

        sg.setCreatedAt(OffsetDateTime.now(userZone));
        sg.setActive(true);

        return sgMapper.toResponse(sgRepository.save(sg));
    }

    @Override
    @Transactional
    public SavingGoalResponse update(Long id, SavingGoalRequest request) {
        SavingGoal existing = sgRepository.findById(id).orElseThrow();

        existing.setDescription(request.getDescription());
        existing.setTargetAmount(request.getTargetAmount());

        ZoneId userZone = existing.getUser().getTimeZone().toZoneId();
        existing.setDeadline(request.getDeadline().atStartOfDay().atZone(userZone).toOffsetDateTime());

        return sgMapper.toResponse(sgRepository.save(existing));
    }

    @Transactional
    @Override
    public void cancel(Long id) {
        SavingGoal existing = sgRepository.findById(id).orElseThrow();
        BigDecimal converted = computeConvertedAccumulated(existing);
        existing.setFrozenAccumulated(converted);
        existing.setState(SavingGoalState.CANCELLED);
        sgRepository.save(existing);
    }

    private BigDecimal computeConvertedAccumulated(SavingGoal sa) {
        String goalCurrency = sa.getCurrencyCode();
        return sa.getTransactions().stream()
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .filter(t -> t.getAmount() != null)
                .map(t -> {
                    String txCurrency = t.getWallet() != null
                            ? t.getWallet().getCurrencyCode()
                            : goalCurrency;
                    return currencyConverter.convert(Money.of(t.getAmount(), txCurrency), goalCurrency);
                })
                .filter(Objects::nonNull)
                .reduce(Money.zero(goalCurrency), Money::add)
                .amount();
    }

    @Override
    @Transactional
    public void disable(Long id) {
        SavingGoal existing = sgRepository.findById(id).orElseThrow();
        ZoneId zoneId = existing.getUser().getTimeZone().toZoneId();
        existing.setActive(false);
        existing.setDisabledAt(OffsetDateTime.now(zoneId));
        sgRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavingGoalResponse> findAllActiveByUser(Long userId) {
        return sgRepository.findAllByUser_IdAndActive(userId, true)
                .stream()
                .peek(sg -> sg.setDisplayAccumulated(computeConvertedAccumulated(sg)))
                .map(sgMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SavingGoalResponse findById(Long id) {
        SavingGoal entity = sgRepository.findById(id).orElseThrow();
        entity.setDisplayAccumulated(computeConvertedAccumulated(entity));
        return sgMapper.toResponse(entity);
    }

    @Transactional
    @Override
    public void checkAllSavingGoals(Long userId) {
        List<SavingGoal> all = sgRepository.findAllByUser_IdAndActiveAndState(userId, true, SavingGoalState.RUNNING);
        for (SavingGoal sa: all) {
            User u = sa.getUser();
            String goalCurrency = sa.getCurrencyCode();

            BigDecimal convertedAccumulated = computeConvertedAccumulated(sa);

            boolean completed = sa.hasReachedTarget(convertedAccumulated);

            if (completed) {
                Transaction last = sa.getTransactions().stream()
                        .filter(t -> Boolean.TRUE.equals(t.getActive()))
                        .max(Comparator.comparing(Transaction::getCreatedAt))
                        .orElseThrow(() -> {
                            log.error("No active transactions found for SavingGoal id={} desc:{} user={}", sa.getId(), sa.getDescription(), userId);
                            return new IllegalStateException("Not active transactions found");
                                });
                sa.setState(SavingGoalState.COMPLETED);
                sa.setCompletedAt(OffsetDateTime.now(u.getTimeZone().toZoneId()));
                sa.setFrozenAccumulated(convertedAccumulated);
                log.info("COMPLETING SavingGoal id={} desc={} convertedAccumulated={}", sa.getId(), sa.getDescription(), convertedAccumulated);

                eventPublisher.publishEvent(new SavingGoalCompletedEvent(
                        sa.getId(),
                        u.getId(),
                        sa.getDescription(),
                        convertedAccumulated,
                        sa.getTargetAmount(),
                        goalCurrency,
                        last.getCreatedAt(),
                        last.getAmount()
                ));
            }
        }
        sgRepository.saveAll(all);
    }
}
