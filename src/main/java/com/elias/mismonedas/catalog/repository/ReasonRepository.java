package com.elias.mismonedas.catalog.repository;

import com.elias.mismonedas.catalog.domain.entity.Reason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReasonRepository extends JpaRepository<Reason, Long> {
    List<Reason> findAllByUser_Id(Long userId);

    List<Reason> findAllByUser_IdAndActiveTrue(Long userId);

    void deleteAllByUser_Id(Long userId);
}
