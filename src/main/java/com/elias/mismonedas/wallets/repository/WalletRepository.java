package com.elias.mismonedas.wallets.repository;

import com.elias.mismonedas.wallets.domain.entity.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    List<Wallet> findByUserId(Long userId);

    Page<Wallet> findByUserId(Long userId, Pageable pageable);

    Optional<Wallet> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_Id(Long userId);

    Optional<Wallet> findByUserIdAndCurrencyCode(Long userId, String currencyCode);

    Optional<Wallet> findByUserIdAndPrimaryTrue(Long userId);

    @Modifying
    @Query("""
            UPDATE Wallet w
               SET w.balance = w.balance + :delta,
                   w.version = COALESCE(w.version, 0) + 1
             WHERE w.id = :id AND w.user.id = :userId
            """)
    int adjustBalance(@Param("id") Long id, @Param("userId") Long userId, @Param("delta") BigDecimal delta);
}
