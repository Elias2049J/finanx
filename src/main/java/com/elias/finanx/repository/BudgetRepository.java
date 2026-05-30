package com.elias.finanx.repository;

import com.elias.finanx.entity.Budget;
import com.elias.finanx.entity.enums.BudgetState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findAllByUser_Id(Long userId);

    List<Budget> findAllByUser_IdAndActiveTrueAndState(Long userId, BudgetState state);
}

