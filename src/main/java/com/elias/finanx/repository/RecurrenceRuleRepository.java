package com.elias.finanx.repository;

import com.elias.finanx.entity.RecurrenceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, Long> {
}

