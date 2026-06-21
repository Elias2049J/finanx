package com.elias.finanx.service.impl;

import com.elias.finanx.entity.RecurrenceRule;
import com.elias.finanx.entity.Schedule;
import com.elias.finanx.entity.enums.RecurrenceType;
import com.elias.finanx.service.RecurrenceCalculatorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class RecurrenceCalculatorServiceImpl implements RecurrenceCalculatorService {

    @Override
    public Optional<OffsetDateTime> computeNextRunAt(Schedule st) {
        RecurrenceRule rule = st.getRecurrenceRule();

        // Base de cálculo: nextRunAt tiene precedencia sobre lastRunAt
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

        // Un único chequeo contra el end de la regla en la zona del usuario,
        // evitando la doble comparación redundante (Instant + LocalDate) que había antes
        LocalDate endDate = rule.getEnd().toInstant().atZone(zone).toLocalDate();
        if (candidate.toLocalDate().isAfter(endDate)) {
            return Optional.empty();
        }

        return Optional.of(candidate.toOffsetDateTime());
    }

    /**
     * Calcula el próximo disparo semanal.
     *
     * - Sin dayOfWeek: avanza exactamente `interval` semanas desde base.
     * - Con dayOfWeek: busca el próximo día de la semana indicado a partir de base,
     *   respetando el intervalo de semanas (salta a la semana base + interval si el
     *   día target ya pasó o es hoy en la semana actual).
     */
    private ZonedDateTime nextWeekly(ZonedDateTime base, RecurrenceRule rule, int interval) {
        DayOfWeek target = rule.getDayOfWeek();

        if (target == null) {
            return base.plusWeeks(interval);
        }

        int baseVal   = base.getDayOfWeek().getValue();  // 1=Mon … 7=Sun
        int targetVal = target.getValue();
        int delta     = targetVal - baseVal;

        // Si el día target ya ocurrió esta semana (o es hoy), avanzamos
        // al comienzo de la próxima ventana de `interval` semanas
        if (delta <= 0) {
            delta += 7 * interval;
        }

        return base.plusDays(delta);
    }

    private ZonedDateTime nextMonthly(ZonedDateTime base, int interval) {
        int desiredDay = base.getDayOfMonth();
        ZonedDateTime plus = base.plusMonths(interval);
        // Clampea al último día del mes si el mes destino es más corto
        int day = Math.min(desiredDay, plus.toLocalDate().lengthOfMonth());
        return plus.withDayOfMonth(day);
    }

    private ZonedDateTime nextYearly(ZonedDateTime base, int interval) {
        int desiredDay   = base.getDayOfMonth();
        int desiredMonth = base.getMonthValue();
        ZonedDateTime plus = base.plusYears(interval);
        // Clampea día (e.g. 29-feb en año no bisiesto → 28-feb)
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