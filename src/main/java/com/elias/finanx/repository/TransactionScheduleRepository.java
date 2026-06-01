package com.elias.finanx.repository;

import com.elias.finanx.entity.TransactionSchedule;
import com.elias.finanx.entity.enums.ScheduleState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TransactionScheduleRepository extends JpaRepository<TransactionSchedule, Long> {
    List<TransactionSchedule> findAllByUser_Id(Long userId);
    List<TransactionSchedule> findAllByActiveTrueAndState(ScheduleState state);

}
