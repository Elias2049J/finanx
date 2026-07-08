package com.elias.mismonedas.dto.analytics.dashboard;

import com.elias.mismonedas.dto.analytics.component.AggregateBy;
import com.elias.mismonedas.dto.analytics.component.Ranking;
import com.elias.mismonedas.dto.analytics.component.TimeLine;
import com.elias.mismonedas.dto.saving.SavingGoalResponse;
import com.elias.mismonedas.entity.enums.SavingGoalState;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SavingGoalsDashboard extends DashboardResponse {
    private List<AggregateBy<SavingGoalState>> byState;

    private TimeLine<SavingGoalResponse> contributionEvolution;
    private TimeLine<LocalDate> dailyContributions;

    private Ranking<SavingGoalResponse> closestToGoal;
    private Ranking<SavingGoalResponse> mostContributed;
}
