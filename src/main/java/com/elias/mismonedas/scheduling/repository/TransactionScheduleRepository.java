package com.elias.mismonedas.scheduling.repository;

import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionScheduleRepository extends JpaRepository<TransactionSchedule, Long> {
    List<TransactionSchedule> findAllByUser_Id(Long userId);
    List<TransactionSchedule> findAllByActiveTrueAndState(ScheduleState state);
    List<TransactionSchedule> findAllByUser_IdAndActiveAndState(Long userId, boolean active, ScheduleState state);

    Page<TransactionSchedule> findAllByUser_IdAndActiveTrue(Long userId, Pageable pageable);

    Page<TransactionSchedule> findAllByUser_IdAndActiveAndState(Long userId, boolean active, ScheduleState state, Pageable pageable);

    List<TransactionSchedule> findByActiveTrueAndStateAndIdGreaterThan(ScheduleState state, Long id, Pageable pageable);

    void deleteAllByUser_Id(Long userId);
}
