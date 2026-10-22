package com.elias.mismonedas.planning.service;

public interface PlanningRecheckService {

    void requestBudgetRecheck(Long userId);

    void requestSavingGoalRecheck(Long userId);
}
