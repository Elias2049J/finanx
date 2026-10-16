package com.elias.mismonedas.ledger.repository;

import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    List<Transaction> findAllByWallet_User_Id(Long userId);

    List<Transaction> findAllByWallet_User_IdAndActiveTrue(Long userId);

    List<Transaction> findAllByWallet_User_IdAndCreatedAtBetweenAndActive(Long userId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, boolean active);

    List<Transaction> findAllByWallet_User_IdAndActive(Long userId, Boolean active);

    List<Transaction> findAllByCategory_IdAndCreatedAtBetweenAndActive(Long categoryId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, Boolean active);

    List<Transaction> findAllByWallet_User_IdAndCreatedAtBetweenAndActive(Long userId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, Boolean active);

    List<Transaction> findAllByCategory_IdAndCreatedAtBetweenAndActiveAndType(
            Long categoryId,
            OffsetDateTime createdAtAfter,
            OffsetDateTime createdAtBefore,
            Boolean active,
            TransactionType type
    );

    List<Transaction> findAllByWallet_User_IdAndCreatedAtBetweenAndActiveAndType(Long userId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, Boolean active, TransactionType type);

    List<Transaction> findAllByWallet_IdAndCreatedAtBetweenAndActive(Long walletId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, Boolean active);

    void deleteAllByWallet_User_Id(Long userId);
}
