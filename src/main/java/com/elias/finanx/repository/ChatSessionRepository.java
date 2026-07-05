package com.elias.finanx.repository;

import com.elias.finanx.dto.ai.ChatSessionResponse;
import com.elias.finanx.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByUuid(String uuid);

    List<ChatSession> findAllByUser_Id(Long userId);
}
