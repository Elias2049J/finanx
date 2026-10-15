package com.elias.mismonedas.document.repository;

import com.elias.mismonedas.document.domain.entity.Receipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    List<Receipt> findAllByUser_Id(Long userId);

    List<Receipt> findAllByUser_IdAndActiveTrue(Long userId);

    Optional<Receipt> findByIdAndUser_Id(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Receipt r where r.id = :id")
    Optional<Receipt> findByIdForUpdate(@Param("id") Long id);

    long countByUser_IdAndCreatedAtAfter(Long userId, OffsetDateTime after);
}
