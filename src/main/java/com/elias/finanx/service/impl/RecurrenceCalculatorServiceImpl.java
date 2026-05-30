package com.elias.finanx.service.impl;

import com.elias.finanx.entity.RecurrenceRule;
import com.elias.finanx.entity.TransactionSchedule;
import com.elias.finanx.service.RecurrenceCalculatorService;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Optional;

@Service
public class RecurrenceCalculatorServiceImpl implements RecurrenceCalculatorService {

    @Override
    public Optional<OffsetDateTime> computeNextRunAt(TransactionSchedule st) {
        if (st == null || st.getUser() == null || st.getUser().getTimeZone() == null) {
            return Optional.empty();
        }

        RecurrenceRule rule = st.getRecurrenceRule();
        if (rule == null || rule.getRecurrenceType() == null) {
            return Optional.empty();
        }

        OffsetDateTime baseOdt = st.getNextRunAt() != null ? st.getNextRunAt() : st.getLastRunAt();
        if (baseOdt == null) {
            return Optional.empty();
        }

        ZoneId zone = st.getUser().getTimeZone().toZoneId();
        ZonedDateTime base = baseOdt.toInstant().atZone(zone);
        int interval = Math.max(rule.getInterval(), 1);
        ZonedDateTime candidate = switch (rule.getRecurrenceType()) {
            case DAILY -> base.plusDays(interval);
            case WEEKLY -> nextWeekly(base, rule);
            case MONTHLY -> nextMonthly(base, interval);
            case YEARLY -> nextYearly(base, interval);
        };
        
        if (st.getEndAt() != null) {
            Instant endInstant = st.getEndAt().toInstant();
            if (candidate.toInstant().isAfter(endInstant)) return Optional.empty();
        }

        if (rule.getEnd() != null) {
            if (candidate.toLocalDate().isAfter(rule.getEnd())) return Optional.empty();
        }

        return Optional.of(candidate.toOffsetDateTime());
    }

    private ZonedDateTime nextWeekly(ZonedDateTime base, RecurrenceRule rule) {
        int interval = Math.max(rule.getInterval(), 1);
        DayOfWeek target = rule.getDayOfWeek();
        if (target == null) {
            return base.plusWeeks(interval);
        }

        int baseVal = base.getDayOfWeek().getValue();
        int targetVal = target.getValue();
        int delta = targetVal - baseVal;

        // Si el día objetivo ya pasó (o es hoy), saltamos al siguiente bloque de semanas según interval.
        if (delta <= 0) {
            delta += 7 * interval;
        }
        return base.plusDays(delta);
    }

    private ZonedDateTime nextMonthly(ZonedDateTime base, int interval) {
        int desiredDay = base.getDayOfMonth();
        ZonedDateTime plus = base.plusMonths(Math.max(interval, 1));
        int lastDay = plus.toLocalDate().lengthOfMonth();
        int day = Math.min(desiredDay, lastDay);
        return plus.withDayOfMonth(day);
    }

    private ZonedDateTime nextYearly(ZonedDateTime base, int interval) {
        int desiredDay = base.getDayOfMonth();
        int desiredMonth = base.getMonthValue();
        ZonedDateTime plus = base.plusYears(Math.max(interval, 1));

        LocalDate firstTry = LocalDate.of(plus.getYear(), desiredMonth, 1);
        int lastDay = firstTry.lengthOfMonth();
        int day = Math.min(desiredDay, lastDay);

        return plus.withMonth(desiredMonth).withDayOfMonth(day);
    }
}
