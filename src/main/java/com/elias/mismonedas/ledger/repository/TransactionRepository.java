package com.elias.mismonedas.ledger.repository;

import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.projection.AmountByCurrency;
import com.elias.mismonedas.ledger.repository.projection.SavingGoalAggregate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    @Override
    @EntityGraph(attributePaths = {"wallet", "wallet.user", "category", "reason"})
    List<Transaction> findAll(Specification<Transaction> spec);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByIdAndUser_Id(Long id, Long userId);

    List<Transaction> findAllByCategory_IdAndCreatedAtBetweenAndActive(Long categoryId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, Boolean active);

    void deleteAllByWallet_User_Id(Long userId);

    @Query("""
            SELECT t.savingGoal.id AS goalId,
                   t.savingGoal.currencyCode AS goalCurrencyCode,
                   t.wallet.currencyCode AS currencyCode,
                   SUM(t.amount) AS total,
                   COUNT(t) AS transactionsCount
            FROM Transaction t
            WHERE t.user.id = :userId AND t.savingGoal.id IN :goalIds AND t.active = true
            GROUP BY t.savingGoal.id, t.savingGoal.currencyCode, t.wallet.currencyCode
            """)
    List<SavingGoalAggregate> sumAmountsByCurrencyForSavingGoals(
            @Param("userId") Long userId, @Param("goalIds") Collection<Long> goalIds);




    @Query("""
            SELECT t.wallet.currencyCode AS currencyCode, SUM(t.amount) AS total
            FROM Transaction t
            WHERE t.user.id = :userId AND t.category.id = :categoryId AND t.type = :type AND t.active = true
              AND t.createdAt >= :from AND t.createdAt <= :to
            GROUP BY t.wallet.currencyCode
            """)
    List<AmountByCurrency> sumAmountsByCurrencyForCategory(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("type") TransactionType type,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    Optional<Transaction> findFirstBySavingGoal_IdAndActiveTrueOrderByCreatedAtDesc(Long savingGoalId);

    Optional<Transaction> findFirstByCategory_IdAndCreatedAtBetweenAndActiveAndTypeOrderByCreatedAtDesc(
            Long categoryId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, Boolean active, TransactionType type);
}
