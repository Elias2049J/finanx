package com.elias.finanx.repository;

import com.elias.finanx.entity.Reason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReasonRepository extends JpaRepository<Reason, Long> {
    List<Reason> findAllByUser_Id(Long userId);
}
