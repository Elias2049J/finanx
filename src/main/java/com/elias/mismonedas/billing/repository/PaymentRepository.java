package com.elias.mismonedas.billing.repository;

import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByUser_Id(Long userId);

    List<Payment> findAllBySubscription_Id(Long subscriptionId);

    Optional<Payment> findByExternalReference(String externalReference);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByExternalReference(String externalReference);

    List<Payment> findAllByState(PaymentState state);

    List<Payment> findAllByUser_IdAndState(Long userId, PaymentState state);
}
