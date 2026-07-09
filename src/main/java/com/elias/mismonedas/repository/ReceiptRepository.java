package com.elias.mismonedas.repository;

import com.elias.mismonedas.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    List<Receipt> findAllByUser_Id(Long userId);

    List<Receipt> findAllByUser_IdAndActiveTrue(Long userId);

    Optional<Receipt> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_IdAndCreatedAtAfter(Long userId, OffsetDateTime after);
}
