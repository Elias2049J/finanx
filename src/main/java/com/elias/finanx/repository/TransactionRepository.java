package com.elias.finanx.repository;

import com.elias.finanx.entity.Transaction;
import com.elias.finanx.entity.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByUser_Id(Long userId);

    List<Transaction> findAllByUser_IdAndActiveTrue(Long userId);

    List<Transaction> findAllByUser_IdAndCreatedAtBetweenAndActive(Long userId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, boolean active);

    List<Transaction> findAllByUser_IdAndActive(Long userId, Boolean active);

    List<Transaction> findAllByCategory_IdAndCreatedAtBetweenAndActive(Long categoryId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, Boolean active);

    List<Transaction> findAllByUser_IdAndCreatedAtBetweenAndActive(Long categoryId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, Boolean active);

    List<Transaction> findAllByCategory_IdAndCreatedAtBetweenAndActiveAndType(
            Long categoryId,
            OffsetDateTime createdAtAfter,
            OffsetDateTime createdAtBefore,
            Boolean active,
            TransactionType type
    );

    List<Transaction> findAllByUser_IdAndCreatedAtBetweenAndActiveAndType(Long userId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, Boolean active, TransactionType type);
}
