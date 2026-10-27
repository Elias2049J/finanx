package com.elias.mismonedas.chat.repository;

import com.elias.mismonedas.chat.domain.entity.ChatMessage;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySession_IdOrderByTurnNumberAsc(Long sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from ChatMessage message where message.session.user.id = :userId")
    int deleteAllBySessionUserId(@Param("userId") Long userId);
}
