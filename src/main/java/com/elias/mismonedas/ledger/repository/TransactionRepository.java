package com.elias.mismonedas.ledger.repository;

import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.projection.AmountByCurrency;
import com.elias.mismonedas.ledger.repository.projection.SavingGoalAggregate;
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
    List<Transaction> findAllByWallet_User_Id(Long userId);

    java.util.Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

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

    /**
     * F6 (opcion B): UNA query agrupada por {@code (meta, moneda)} que alimenta
     * {@code SavingGoalTotals} para todas las metas de la lista a la vez — sin N+1.
     * {@code COUNT(t)} por grupo debe sumarse entre monedas para el total de aportaciones.
     */
    @Query("""
            SELECT t.savingGoal.id AS goalId,
                   t.savingGoal.currencyCode AS goalCurrencyCode,
                   t.wallet.currencyCode AS currencyCode,
                   SUM(t.amount) AS total,
                   COUNT(t) AS transactionsCount
            FROM Transaction t
            WHERE t.savingGoal.id IN :goalIds AND t.active = true
            GROUP BY t.savingGoal.id, t.savingGoal.currencyCode, t.wallet.currencyCode
            """)
    List<SavingGoalAggregate> sumAmountsByCurrencyForSavingGoals(@Param("goalIds") Collection<Long> goalIds);

    /**
     * F6 (opcion B): gastado de un presupuesto agregado en la base, acotado al periodo.
     */
    @Query("""
            SELECT t.wallet.currencyCode AS currencyCode, SUM(t.amount) AS total
            FROM Transaction t
            WHERE t.category.id = :categoryId AND t.type = :type AND t.active = true
              AND t.createdAt >= :from AND t.createdAt <= :to
            GROUP BY t.wallet.currencyCode
            """)
    List<AmountByCurrency> sumAmountsByCurrencyForCategory(
            @Param("categoryId") Long categoryId,
            @Param("type") TransactionType type,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /** Ultima transaccion activa de una meta — solo para el payload del evento de completion. */
    Optional<Transaction> findFirstBySavingGoal_IdAndActiveTrueOrderByCreatedAtDesc(Long savingGoalId);

    /** Ultima transaccion activa de una categoria en el periodo — payload de las alertas de budget. */
    Optional<Transaction> findFirstByCategory_IdAndCreatedAtBetweenAndActiveAndTypeOrderByCreatedAtDesc(
            Long categoryId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, Boolean active, TransactionType type);
}
