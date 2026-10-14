package com.elias.mismonedas.scheduling.repository;

import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findAllByUser_IdAndActiveAndState(Long userId, Boolean active, ScheduleState state);

    List<Schedule> findAllByUser_Id(Long userId);

    List<Schedule> findAllByUser_IdAndActiveTrue(Long userId);

    void deleteAllByUser_Id(Long userId);
}
