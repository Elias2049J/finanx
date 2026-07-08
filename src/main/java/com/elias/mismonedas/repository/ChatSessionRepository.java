package com.elias.mismonedas.repository;

import com.elias.mismonedas.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByUuid(String uuid);

    List<ChatSession> findAllByUser_Id(Long userId);
}
