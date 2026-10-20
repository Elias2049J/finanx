package com.elias.mismonedas.scheduling.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.domain.enums.RecurrenceType;
import com.elias.mismonedas.scheduling.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.mismonedas.scheduling.dto.recurrencerule.RecurrenceRuleResponse;
import com.elias.mismonedas.scheduling.mapper.RecurrenceRuleMapper;
import com.elias.mismonedas.scheduling.repository.RecurrenceRuleRepository;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecurrenceServiceImpl implements RecurrenceService {
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final RecurrenceRuleMapper recurrenceRuleMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RecurrenceRule create(Long userId, RecurrenceRuleRequest request) {
        RecurrenceRule recurrenceRule = recurrenceRuleMapper.toEntity(request);

        User user = userRepository.getReferenceById(userId);
        recurrenceRule.setUser(user);

        ZoneId userZone = user.getTimeZone().toZoneId();
        recurrenceRule.setStart(request.getStart().atStartOfDay().atZone(userZone).toOffsetDateTime());
        recurrenceRule.setEnd(request.getEnd().atStartOfDay().atZone(userZone).toOffsetDateTime());
        return recurrenceRuleRepository.save(recurrenceRule);
    }

    @Override
    @Transactional
    public RecurrenceRule update(Long id, RecurrenceRuleRequest request) {
        RecurrenceRule existing = recurrenceRuleRepository.findById(id).orElseThrow();
        recurrenceRuleMapper.updateFromDto(request, existing);

        ZoneId userZone = existing.getUser().getTimeZone().toZoneId();

        existing.setStart(request.getStart().atStartOfDay().atZone(userZone).toOffsetDateTime());
        existing.setEnd(request.getEnd().atStartOfDay().atZone(userZone).toOffsetDateTime());

        return recurrenceRuleRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public RecurrenceRuleResponse findById(Long id) {
        return recurrenceRuleMapper.toResponse(recurrenceRuleRepository.findById(id).orElseThrow());
    }

    @Override
    public Optional<OffsetDateTime> computeNextRunAt(Schedule st) {
        RecurrenceRule rule = st.getRecurrenceRule();

        OffsetDateTime baseOdt = st.getNextRunAt() != null
                ? st.getNextRunAt()
                : st.getLastRunAt();

        ZoneId zone = st.getUser().getTimeZone().toZoneId();
        ZonedDateTime base = baseOdt.toInstant().atZone(zone);

        if (rule.getRecurrenceType() == RecurrenceType.NONE) {
            return Optional.empty();
        }

        int interval = Math.max(rule.getInterval(), 1);

        ZonedDateTime candidate = switch (rule.getRecurrenceType()) {
            case DAILY -> base.plusDays(interval);
            case WEEKLY -> nextWeekly(base, rule, interval);
            case MONTHLY -> nextMonthly(base, interval);
            case YEARLY -> nextYearly(base, interval);
            case NONE -> throw new IllegalStateException("Unexpected recurrence type NONE");
        };

        LocalDate endDate = rule.getEnd().toInstant().atZone(zone).toLocalDate();
        if (candidate.toLocalDate().isAfter(endDate)) {
            return Optional.empty();
        }

        return Optional.of(candidate.toOffsetDateTime());
    }

    private ZonedDateTime nextWeekly(ZonedDateTime base, RecurrenceRule rule, int interval) {
        DayOfWeek target = rule.getDayOfWeek();

        if (target == null) {
            return base.plusWeeks(interval);
        }

        int baseVal = base.getDayOfWeek().getValue();
        int targetVal = target.getValue();
        int delta = targetVal - baseVal;

        if (delta <= 0) {
            delta += 7 * interval;
        }

        return base.plusDays(delta);
    }

    private ZonedDateTime nextMonthly(ZonedDateTime base, int interval) {
        int desiredDay = base.getDayOfMonth();
        ZonedDateTime plus = base.plusMonths(interval);
        int day = Math.min(desiredDay, plus.toLocalDate().lengthOfMonth());
        return plus.withDayOfMonth(day);
    }

    private ZonedDateTime nextYearly(ZonedDateTime base, int interval) {
        int desiredDay = base.getDayOfMonth();
        int desiredMonth = base.getMonthValue();
        ZonedDateTime plus = base.plusYears(interval);
        int day = Math.min(desiredDay, LocalDate.of(plus.getYear(), desiredMonth, 1).lengthOfMonth());
        return plus.withMonth(desiredMonth).withDayOfMonth(day);
    }

    @Override
    public OffsetDateTime computeCurrentPeriodStart(RecurrenceRule rr, OffsetDateTime now, ZoneId zoneId) {
        int interval = Math.max(rr.getInterval(), 1);
        ZonedDateTime start = rr.getStart().toInstant().atZone(zoneId);
        ZonedDateTime current = now.toInstant().atZone(zoneId);

        if (current.isBefore(start)) {
            return start.toOffsetDateTime();
        }

        return switch (rr.getRecurrenceType()) {
            case NONE -> start.toOffsetDateTime();
            case DAILY -> {
                long cycles = ChronoUnit.DAYS.between(start.toLocalDate(), current.toLocalDate()) / interval;
                yield start.plusDays(cycles * interval).toOffsetDateTime();
            }
            case WEEKLY -> {
                long cycles = ChronoUnit.WEEKS.between(start.toLocalDate(), current.toLocalDate()) / interval;
                yield start.plusWeeks(cycles * interval).toOffsetDateTime();
            }
            case MONTHLY -> {
                long cycles = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(current)) / interval;
                yield start.plusMonths(cycles * interval).toOffsetDateTime();
            }
            case YEARLY -> {
                long cycles = ChronoUnit.YEARS.between(start.toLocalDate(), current.toLocalDate()) / interval;
                yield start.plusYears(cycles * interval).toOffsetDateTime();
            }
        };
    }
}
