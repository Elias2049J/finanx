package com.elias.finanx.repository;

import com.elias.finanx.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    boolean existsByExternalEventId(String externalEventId);

    Optional<WebhookEvent> findByExternalEventId(String externalEventId);
}
