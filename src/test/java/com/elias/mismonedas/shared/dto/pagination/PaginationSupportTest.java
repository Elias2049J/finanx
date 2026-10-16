package com.elias.mismonedas.shared.dto.pagination;

import com.elias.mismonedas.shared.domain.vo.DateRange;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationSupportTest {

    @Test
    void shouldConvertInclusiveCalendarDatesIntoHalfOpenUserZoneRange() {
        DateRange range = new DateRange(LocalDate.parse("2026-06-15"), LocalDate.parse("2026-06-30"));
        ZoneId lima = ZoneId.of("America/Lima");

        assertThat(range.startInclusive(lima).toLocalDate()).isEqualTo(LocalDate.parse("2026-06-15"));
        assertThat(range.startInclusive(lima).toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
        assertThat(range.endExclusive(lima).toLocalDate()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(range.endExclusive(lima).toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
        assertThat(range.coveringMonths())
                .isEqualTo(new DateRange(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30")));
    }

    @Test
    void shouldRejectIncompleteOrReversedOptionalRange() {
        assertThatThrownBy(() -> DateRange.optional(LocalDate.parse("2026-06-01"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DateRange(LocalDate.parse("2026-06-02"), LocalDate.parse("2026-06-01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(DateRange.optional(null, null)).isEmpty();
    }

    @Test
    void shouldClampPageSizeAndAppendStableIdSort() {
        var pageable = PageRequest.of(2, 500, Sort.by(Sort.Direction.ASC, "createdAt"));

        var stable = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(stable.getPageNumber()).isEqualTo(2);
        assertThat(stable.getPageSize()).isEqualTo(PaginationDefaults.MAX_PAGE_SIZE);
        assertThat(stable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(stable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void shouldMapSpringPageIntoStableResponseEnvelope() {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponseFactory.from(page, String::toUpperCase);

        assertThat(response.items()).containsExactly("A", "B");
        assertThat(response.pagination()).isEqualTo(new PaginationResponse(1, 2, 5, 3));
    }
}
