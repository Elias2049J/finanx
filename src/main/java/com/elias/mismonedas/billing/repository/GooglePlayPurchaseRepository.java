package com.elias.mismonedas.billing.repository;

import com.elias.mismonedas.billing.domain.entity.GooglePlayPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GooglePlayPurchaseRepository extends JpaRepository<GooglePlayPurchase, Long> {

    Optional<GooglePlayPurchase> findByPurchaseToken(String purchaseToken);
}
