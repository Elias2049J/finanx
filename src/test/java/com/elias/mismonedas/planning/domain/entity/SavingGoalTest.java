package com.elias.mismonedas.planning.domain.entity;

import com.elias.mismonedas.planning.domain.enums.SavingGoalState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SavingGoalTest {

    @Test
    void shouldReportTargetReachedForConvertedAccumulatedAmount() {
        SavingGoal goal = goal(new BigDecimal("100.00"));

        assertThat(goal.hasReachedTarget(new BigDecimal("100.00"))).isTrue();
        assertThat(goal.hasReachedTarget(new BigDecimal("125.00"))).isTrue();
        assertThat(goal.hasReachedTarget(new BigDecimal("99.99"))).isFalse();
    }

    @Test
    void shouldUseAccumulatedAmountAndRunningStateForIsCompleted() {
        SavingGoal goal = goal(new BigDecimal("100.00"));
        goal.setDisplayAccumulated(new BigDecimal("100.00"));

        assertThat(goal.isCompleted()).isTrue();

        goal.setState(SavingGoalState.CANCELLED);
        assertThat(goal.isCompleted()).isFalse();
    }

    private SavingGoal goal(BigDecimal targetAmount) {
        SavingGoal goal = new SavingGoal();
        goal.setTargetAmount(targetAmount);
        goal.setState(SavingGoalState.RUNNING);
        return goal;
    }
}
