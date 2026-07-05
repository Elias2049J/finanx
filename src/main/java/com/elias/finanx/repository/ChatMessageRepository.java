package com.elias.finanx.repository;

import com.elias.finanx.entity.ChatMessage;
import com.elias.finanx.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySession_IdOrderByTurnNumberAsc(Long sessionId);
}
