package com.elias.mismonedas.planning.repository;

import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.planning.domain.enums.SavingGoalState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingGoalRepository extends JpaRepository<SavingGoal, Long> {

	Optional<SavingGoal> findByIdAndUser_Id(Long id, Long userId);

	List<SavingGoal> findAllByUser_IdAndActive(Long userId, Boolean active);

	List<SavingGoal> findAllByUser_IdAndActiveAndState(Long userId, Boolean active, SavingGoalState state);

    void deleteAllByUser_Id(Long userId);
}
