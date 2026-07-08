package com.elias.mismonedas.repository;

import com.elias.mismonedas.entity.BudgetSchedule;
import com.elias.mismonedas.entity.enums.ScheduleState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetScheduleRepository extends JpaRepository<BudgetSchedule, Long> {

    List<BudgetSchedule> findAllByActiveTrueAndState(ScheduleState scheduleState);

    List<BudgetSchedule> findAllByUser_IdAndActiveAndState(Long userId, boolean active, ScheduleState scheduleState);

    void deleteAllByUser_Id(Long userId);
}

