package com.elias.mismonedas.scheduling.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.domain.enums.RecurrenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceServiceImplTest {

    private static final ZoneId LIMA = TimeZone.AMERICA_LIMA.toZoneId();

    private RecurrenceServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RecurrenceServiceImpl(null, null, null);
        user = new User();
        user.setId(1L);
        user.setPreferences(UserPreferences.builder().timeZone(TimeZone.AMERICA_LIMA).build());
    }

    @Test
    void monthlyRecurrenceKeepsAnchorDayAfterAShortMonth() {
        RecurrenceRule rule = rule(RecurrenceType.MONTHLY, 1, date(2023, 1, 31));
        BudgetSchedule schedule = schedule(rule, date(2023, 1, 31));

        assertThat(service.computeNextRunAt(schedule)).contains(date(2023, 2, 28));

        schedule.setNextRunAt(date(2023, 2, 28));

        assertThat(service.computeNextRunAt(schedule)).contains(date(2023, 3, 31));
    }

    @Test
    void yearlyRecurrenceReturnsToLeapDayAfterNonLeapYears() {
        RecurrenceRule rule = rule(RecurrenceType.YEARLY, 1, date(2024, 2, 29));
        rule.setEnd(date(2030, 1, 1));
        BudgetSchedule schedule = schedule(rule, date(2027, 2, 28));

        assertThat(service.computeNextRunAt(schedule)).contains(date(2028, 2, 29));
    }

    @Test
    void weeklyRecurrenceAdvancesByIntervalWhenBaseIsOnTargetDay() {
        RecurrenceRule rule = rule(RecurrenceType.WEEKLY, 2, date(2024, 1, 3));
        rule.setDayOfWeek(DayOfWeek.WEDNESDAY);
        BudgetSchedule schedule = schedule(rule, date(2024, 1, 3));

        assertThat(service.computeNextRunAt(schedule)).contains(date(2024, 1, 17));
    }

    @Test
    void weeklyRecurrenceFindsTargetDayWhenBaseIsBeforeIt() {
        RecurrenceRule rule = rule(RecurrenceType.WEEKLY, 1, date(2024, 1, 1));
        rule.setDayOfWeek(DayOfWeek.WEDNESDAY);
        BudgetSchedule schedule = schedule(rule, date(2024, 1, 1));

        assertThat(service.computeNextRunAt(schedule)).contains(date(2024, 1, 3));
    }

    private RecurrenceRule rule(RecurrenceType type, int interval, OffsetDateTime start) {
        RecurrenceRule rule = new RecurrenceRule();
        rule.setRecurrenceType(type);
        rule.setInterval(interval);
        rule.setStart(start);
        rule.setEnd(start.plusYears(5));
        rule.setUser(user);
        return rule;
    }

    private BudgetSchedule schedule(RecurrenceRule rule, OffsetDateTime nextRunAt) {
        BudgetSchedule schedule = new BudgetSchedule();
        schedule.setUser(user);
        schedule.setRecurrenceRule(rule);
        schedule.setNextRunAt(nextRunAt);
        return schedule;
    }

    private OffsetDateTime date(int year, int month, int day) {
        return LocalDate.of(year, month, day).atTime(9, 0).atZone(LIMA).toOffsetDateTime();
    }
}
