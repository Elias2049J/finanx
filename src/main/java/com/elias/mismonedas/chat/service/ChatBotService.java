package com.elias.mismonedas.chat.service;

import com.elias.mismonedas.chat.dto.ai.ChatMessageResponse;
import com.elias.mismonedas.chat.dto.ai.ChatSessionResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatBotService {
    ChatSessionResponse startSession();

    ChatSessionResponse closeSession(Long sessionId);

    ChatSessionResponse findSessionById(Long sessionId);

    ChatSessionResponse findSessionByUUID(String sessionId);

    List<ChatSessionResponse> findAllSessionsByUser(Long userId);

    PageResponse<ChatSessionResponse> listSessionsByUser(Pageable pageable);

    boolean isSessionActive(Long sessionId, Long userId);

    @Transactional
    ChatMessageResponse sendMessage(Long sessionId, String rawUserPrompt);
}
