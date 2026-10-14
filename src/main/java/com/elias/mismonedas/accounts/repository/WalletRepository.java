package com.elias.mismonedas.accounts.repository;

import com.elias.mismonedas.accounts.domain.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    List<Wallet> findByUserId(Long userId);

    long countByUser_Id(Long userId);

    Optional<Wallet> findByUserIdAndCurrencyCode(Long userId, String currencyCode);

    Optional<Wallet> findByUserIdAndPrimaryTrue(Long userId);
}
