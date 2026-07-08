package com.elias.finanx.repository;

import com.elias.finanx.entity.Payment;
import com.elias.finanx.entity.enums.PaymentState;
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
