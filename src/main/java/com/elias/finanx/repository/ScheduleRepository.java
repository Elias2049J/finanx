package com.elias.finanx.repository;

import com.elias.finanx.entity.Schedule;
import com.elias.finanx.entity.enums.ScheduleState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findAllByActiveAndState(Boolean active, ScheduleState state);

    List<Schedule> findAllByUser_Id(Long userId);
}
