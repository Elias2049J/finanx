package com.elias.mismonedas.planning.domain.entity;

import com.elias.mismonedas.planning.domain.enums.BudgetHealth;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetTest {

    @Test
    void shouldSetHealthToExceededWhenSpentReachesLimit() {
        Budget budget = budget(true, BudgetHealth.OK);

        BudgetHealth health = budget.updateHealth(new BigDecimal("100.00"));

        assertThat(health).isEqualTo(BudgetHealth.EXCEEDED);
        assertThat(budget.getHealth()).isEqualTo(BudgetHealth.EXCEEDED);
    }

    @Test
    void shouldSetHealthToNearLimitAtConfiguredThreshold() {
        Budget budget = budget(true, BudgetHealth.OK);

        BudgetHealth health = budget.updateHealth(new BigDecimal("80.00"));

        assertThat(health).isEqualTo(BudgetHealth.NEAR_LIMIT);
    }

    @Test
    void shouldReturnToOkAndResetNotificationFlagsBelowThreshold() {
        Budget budget = budget(true, BudgetHealth.NEAR_LIMIT);
        budget.setNearLimitNotified(true);
        budget.setExceededNotified(true);

        BudgetHealth health = budget.updateHealth(new BigDecimal("50.00"));

        assertThat(health).isEqualTo(BudgetHealth.OK);
        assertThat(budget.getNearLimitNotified()).isFalse();
        assertThat(budget.getExceededNotified()).isFalse();
    }

    @Test
    void shouldNotResetNotificationFlagsWhenAlertsAreDisabled() {
        Budget budget = budget(false, BudgetHealth.EXCEEDED);
        budget.setNearLimitNotified(true);
        budget.setExceededNotified(true);

        BudgetHealth health = budget.updateHealth(new BigDecimal("50.00"));

        assertThat(health).isEqualTo(BudgetHealth.OK);
        assertThat(budget.getNearLimitNotified()).isTrue();
        assertThat(budget.getExceededNotified()).isTrue();
    }

    @Test
    void shouldMarkExceededNotificationOnlyOnce() {
        Budget budget = budget(true, BudgetHealth.EXCEEDED);

        assertThat(budget.markExceededNotified()).isTrue();
        assertThat(budget.markExceededNotified()).isFalse();
        assertThat(budget.getExceededNotified()).isTrue();
    }

    private Budget budget(boolean alertable, BudgetHealth health) {
        return Budget.builder()
                .alertable(alertable)
                .percentAlert(80)
                .limitAmount(new BigDecimal("100.00"))
                .health(health)
                .build();
    }
}
