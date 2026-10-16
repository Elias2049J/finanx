package com.elias.mismonedas.scheduling.repository;

import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findByIdAndUser_Id(Long id, Long userId);

    List<Schedule> findAllByUser_IdAndActiveAndState(Long userId, Boolean active, ScheduleState state);

    List<Schedule> findAllByUser_Id(Long userId);

    List<Schedule> findAllByUser_IdAndActiveTrue(Long userId);

    Page<Schedule> findAllByUser_IdAndActiveTrue(Long userId, Pageable pageable);

    Page<Schedule> findAllByUser_IdAndActiveAndState(Long userId, Boolean active, ScheduleState state, Pageable pageable);

    void deleteAllByUser_Id(Long userId);
}
