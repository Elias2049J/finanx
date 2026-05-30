package com.elias.finanx.repository;

import com.elias.finanx.entity.SavingGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavingGoalRepository extends JpaRepository<SavingGoal, Long> {

	List<SavingGoal> findAllByUser_IdAndActive(Long userId, Boolean active);
}
