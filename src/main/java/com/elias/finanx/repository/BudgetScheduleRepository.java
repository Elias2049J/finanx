package com.elias.finanx.repository;

import com.elias.finanx.entity.BudgetSchedule;
import com.elias.finanx.entity.enums.ScheduleState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetScheduleRepository extends JpaRepository<BudgetSchedule, Long> {

    List<BudgetSchedule> findAllByActiveTrueAndState(ScheduleState scheduleState);
}

