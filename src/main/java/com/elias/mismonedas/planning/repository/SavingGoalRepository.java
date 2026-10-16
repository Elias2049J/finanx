package com.elias.mismonedas.planning.repository;

import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.planning.domain.enums.SavingGoalState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingGoalRepository extends JpaRepository<SavingGoal, Long> {

	Optional<SavingGoal> findByIdAndUser_Id(Long id, Long userId);

 List<SavingGoal> findAllByUser_IdAndActive(Long userId, Boolean active);

    Page<SavingGoal> findAllByUser_IdAndActive(Long userId, Boolean active, Pageable pageable);

 List<SavingGoal> findAllByUser_IdAndActiveAndState(Long userId, Boolean active, SavingGoalState state);

    Page<SavingGoal> findAllByUser_IdAndActiveAndState(Long userId, Boolean active, SavingGoalState state, Pageable pageable);

    void deleteAllByUser_Id(Long userId);
}
