package com.elias.mismonedas.shared.domain.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.Optional;

public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both from and to dates are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from date must not be after to date");
        }
    }

    public static Optional<DateRange> optional(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return Optional.empty();
        }
        return Optional.of(new DateRange(from, to));
    }

    public OffsetDateTime startInclusive(ZoneId zoneId) {
        return from.atStartOfDay(zoneId).toOffsetDateTime();
    }

    public OffsetDateTime endExclusive(ZoneId zoneId) {
        return to.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();
    }

    public DateRange coveringMonths() {
        return new DateRange(
                YearMonth.from(from).atDay(1),
                YearMonth.from(to).atEndOfMonth()
        );
    }
}
