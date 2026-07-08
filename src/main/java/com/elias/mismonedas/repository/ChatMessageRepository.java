package com.elias.mismonedas.repository;

import com.elias.mismonedas.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySession_IdOrderByTurnNumberAsc(Long sessionId);
}
