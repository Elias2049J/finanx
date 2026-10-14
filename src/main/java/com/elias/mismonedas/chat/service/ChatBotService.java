package com.elias.mismonedas.chat.service;

import com.elias.mismonedas.chat.dto.ai.ChatMessageResponse;
import com.elias.mismonedas.chat.dto.ai.ChatSessionResponse;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatBotService {
    ChatSessionResponse startSession(Long userId);

    ChatSessionResponse closeSession(Long sessionId, Long userId);

    ChatSessionResponse findSessionById(Long sessionId, Long userId);

    ChatSessionResponse findSessionByUUID(String sessionId);

    List<ChatSessionResponse> findAllSessionsByUser(Long userId);

    boolean isSessionActive(Long sessionId, Long userId);

    @Transactional
    ChatMessageResponse sendMessage(Long sessionId, Long userId, String rawUserPrompt);
}
