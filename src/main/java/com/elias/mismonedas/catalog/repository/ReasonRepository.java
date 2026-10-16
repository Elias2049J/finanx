package com.elias.mismonedas.catalog.repository;

import com.elias.mismonedas.catalog.domain.entity.Reason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReasonRepository extends JpaRepository<Reason, Long>, JpaSpecificationExecutor<Reason> {
    List<Reason> findAllByUser_Id(Long userId);

    Optional<Reason> findByIdAndUser_Id(Long id, Long userId);

    List<Reason> findAllByUser_IdAndActiveTrue(Long userId);

    void deleteAllByUser_Id(Long userId);
}
