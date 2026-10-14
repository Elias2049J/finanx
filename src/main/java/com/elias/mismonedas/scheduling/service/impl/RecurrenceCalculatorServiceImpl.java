package com.elias.mismonedas.scheduling.service.impl;

import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.domain.enums.RecurrenceType;
import com.elias.mismonedas.scheduling.service.RecurrenceCalculatorService;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class RecurrenceCalculatorServiceImpl implements RecurrenceCalculatorService {

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
            case DAILY   -> base.plusDays(interval);
            case WEEKLY  -> nextWeekly(base, rule, interval);
            case MONTHLY -> nextMonthly(base, interval);
            case YEARLY  -> nextYearly(base, interval);
            case NONE    -> throw new IllegalStateException("Unexpected recurrence type NONE");
        };

        LocalDate endDate = rule.getEnd().toInstant().atZone(zone).toLocalDate();
        if (candidate.toLocalDate().isAfter(endDate)) {
            return Optional.empty();
        }

        return Optional.of(candidate.toOffsetDateTime());
    }

    /**
     * Calcula el proximo disparo semanal.
     * <p>
     * - Sin dayOfWeek: avanza exactamente `interval` semanas desde base.
     * - Con dayOfWeek: busca el proximo dia de la semana indicado a partir de base,
     *   respetando el intervalo de semanas (salta a la semana base + interval si el
     *   dia target ya paso o es hoy en la semana actual).
     */
    private ZonedDateTime nextWeekly(ZonedDateTime base, RecurrenceRule rule, int interval) {
        DayOfWeek target = rule.getDayOfWeek();

        if (target == null) {
            return base.plusWeeks(interval);
        }

        int baseVal   = base.getDayOfWeek().getValue();
        int targetVal = target.getValue();
        int delta     = targetVal - baseVal;

        if (delta <= 0) {
            delta += 7 * interval;
        }

        return base.plusDays(delta);
    }

    private ZonedDateTime nextMonthly(ZonedDateTime base, int interval) {
        int desiredDay = base.getDayOfMonth();
        ZonedDateTime plus = base.plusMonths(interval);
        // Clampea al último dia del mes si el mes destino es más corto
        int day = Math.min(desiredDay, plus.toLocalDate().lengthOfMonth());
        return plus.withDayOfMonth(day);
    }

    private ZonedDateTime nextYearly(ZonedDateTime base, int interval) {
        int desiredDay   = base.getDayOfMonth();
        int desiredMonth = base.getMonthValue();
        ZonedDateTime plus = base.plusYears(interval);
        // Clampea dia (e.g. 29-feb en año no bisiesto → 28-feb)
        int day = Math.min(desiredDay, LocalDate.of(plus.getYear(), desiredMonth, 1).lengthOfMonth());
        return plus.withMonth(desiredMonth).withDayOfMonth(day);
    }

    @Override
    public OffsetDateTime computeCurrentPeriodStart(RecurrenceRule rr, OffsetDateTime now, ZoneId zoneId) {
        int interval = Math.max(rr.getInterval(), 1);
        ZonedDateTime start   = rr.getStart().toInstant().atZone(zoneId);
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