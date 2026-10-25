package com.elias.mismonedas.ledger.repository;

import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.projection.AmountByCurrency;
import com.elias.mismonedas.ledger.repository.projection.AnalyticsDailyAggregate;
import com.elias.mismonedas.ledger.repository.projection.AnalyticsDimensionAggregate;
import com.elias.mismonedas.ledger.repository.projection.SavingGoalAggregate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Desactiva la transaccion solo si sigue activa y pertenece al usuario.
     *
     * <p>Es un claim atomico: dos DELETE concurrentes compiten por el mismo
     * UPDATE y solo uno obtiene {@code 1}, de modo que la reversion de saldo
     * se aplica una sola vez.
     *
     * @return 1 si esta llamada desactivo la transaccion; 0 si ya estaba inactiva
     *         o no pertenece al usuario
     */
    @Modifying
    @Query("""
            UPDATE Transaction t
               SET t.active = false,
                   t.disabledAt = :now
             WHERE t.id = :id
               AND t.user.id = :userId
               AND t.active = true
            """)
    int deactivateIfActive(@Param("id") Long id,
                           @Param("userId") Long userId,
                           @Param("now") OffsetDateTime now);

    /** Only movements owned by the budget owner can contribute to its spend. */
    List<Transaction> findAllByUser_IdAndCategory_IdAndCreatedAtBetweenAndActive(
            Long userId,
            Long categoryId,
            OffsetDateTime createdAtAfter,
            OffsetDateTime createdAtBefore,
            Boolean active);

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

    @Query("""
            SELECT t.wallet.currencyCode AS currencyCode,
                   t.type AS type,
                   t.paymentMethod AS paymentMethod,
                   c.id AS categoryId,
                   c.name AS categoryName,
                   r.id AS reasonId,
                   r.description AS reasonDescription,
                   SUM(t.amount) AS total,
                   COUNT(t) AS occurrences,
                   MIN(t.amount) AS minAmount,
                   MAX(t.amount) AS maxAmount
              FROM Transaction t
              LEFT JOIN t.category c
              LEFT JOIN t.reason r
             WHERE t.user.id = :userId
               AND t.active = true
               AND t.createdAt >= :from
               AND t.createdAt < :to
               AND (:walletId IS NULL OR t.wallet.id = :walletId)
             GROUP BY t.wallet.currencyCode, t.type, t.paymentMethod,
                      c.id, c.name, r.id, r.description
            """)
    List<AnalyticsDimensionAggregate> aggregateByDimensions(
            @Param("userId") Long userId,
            @Param("walletId") Long walletId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query(value = """
            SELECT CAST(t.fecha_creacion AT TIME ZONE :zone AS date) AS day,
                   t.tipo AS type,
                   w.moneda AS currencyCode,
                   SUM(t.monto) AS total,
                   COUNT(*) AS occurrences
              FROM movimientos t
              JOIN billeteras w ON w.id = t.id_billetera
             WHERE t.id_usuario = :userId
               AND t.activo = true
               AND t.fecha_creacion >= :from
               AND t.fecha_creacion < :to
               AND (:walletId IS NULL OR t.id_billetera = :walletId)
             GROUP BY 1, 2, 3
             ORDER BY 1
            """, nativeQuery = true)
    List<AnalyticsDailyAggregate> aggregateDaily(
            @Param("userId") Long userId,
            @Param("walletId") Long walletId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("zone") String zone);

    Optional<Transaction> findFirstBySavingGoal_IdAndActiveTrueOrderByCreatedAtDesc(Long savingGoalId);

    Optional<Transaction> findFirstByCategory_IdAndCreatedAtBetweenAndActiveAndTypeOrderByCreatedAtDesc(
            Long categoryId, OffsetDateTime createdAtAfter, OffsetDateTime createdAtBefore, Boolean active, TransactionType type);
}
