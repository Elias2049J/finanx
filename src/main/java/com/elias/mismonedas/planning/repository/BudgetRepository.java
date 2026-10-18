package com.elias.mismonedas.planning.repository;

import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.enums.BudgetHealth;
import com.elias.mismonedas.planning.domain.enums.BudgetState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long>, JpaSpecificationExecutor<Budget> {
    List<Budget> findAllByUser_Id(Long userId);

    Optional<Budget> findByIdempotencyKey(String idempotencyKey);

    Optional<Budget> findByIdAndUser_Id(Long id, Long userId);

    List<Budget> findAllByUser_IdAndActiveTrue(Long userId);

    List<Budget> findAllByUser_IdAndActiveTrueAndState(Long userId, BudgetState state);

    List<Budget> findAllByUser_IdAndActiveAndCreatedAtBetweenAndStateAndHealth(
            Long userId,
            Boolean active,
            OffsetDateTime createdAtAfter,
            OffsetDateTime createdAtBefore,
            BudgetState state,
            BudgetHealth health
    );

    List<Budget> findAllByUser_IdAndActiveAndCreatedAtBetweenAndState(Long userId, Boolean active, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, BudgetState state);

    void deleteAllByUser_Id(Long userId);
}

