package com.elias.mismonedas.chat.service.impl;

import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.billing.plan.RequiresPlan;
import com.elias.mismonedas.chat.domain.entity.ChatMessage;
import com.elias.mismonedas.chat.domain.entity.ChatSession;
import com.elias.mismonedas.chat.domain.enums.ChatRole;
import com.elias.mismonedas.chat.domain.enums.ChatSessionState;
import com.elias.mismonedas.chat.domain.enums.ChatSkill;
import com.elias.mismonedas.chat.dto.ai.ChatMessageRequest;
import com.elias.mismonedas.chat.dto.ai.ChatMessageResponse;
import com.elias.mismonedas.chat.dto.ai.ChatSessionResponse;
import com.elias.mismonedas.chat.dto.ai.PromptRequest;
import com.elias.mismonedas.chat.mapper.ChatMessageMapper;
import com.elias.mismonedas.chat.mapper.ChatSessionMapper;
import com.elias.mismonedas.chat.port.ChatPort;
import com.elias.mismonedas.chat.repository.ChatMessageRepository;
import com.elias.mismonedas.chat.repository.ChatSessionRepository;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.elias.mismonedas.chat.service.ChatBotService;
import com.elias.mismonedas.chat.util.AiUtils;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.insights.domain.entity.UserDataContext;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.insights.service.UserDataContextService;
import com.elias.mismonedas.shared.config.AiProperties;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;










@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBotServiceImpl implements ChatBotService {

    private final ChatPort chatPort;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserDataContextService userDataContextService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AiProperties aiProperties;
    private final SecurityService securityService;

    @Transactional
    @Override
    @RequiresPlan(SubscriptionPlan.PREMIUM)
    public ChatSessionResponse startSession() {
        User user = securityService.getCurrentUser();
        Long userId = user.getId();
        UserDataContext dataContext = refreshUserDataContext(userId);

        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setFinancialContext(buildSessionContext(dataContext));

        ChatSession saved = chatSessionRepository.save(session);
        log.info("[CHAT] Sesion iniciada: {} para usuario: {}", saved.getUuid(), userId);
        return chatSessionMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public ChatSessionResponse closeSession(Long sessionId) {
        Long userId = securityService.getCurrentUser().getId();
        ChatSession session = getSessionEntity(sessionId, userId);
        session.closeSession();
        return chatSessionMapper.toResponse(chatSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    @Override
    public ChatSessionResponse findSessionById(Long sessionId) {
        Long userId = securityService.getCurrentUser().getId();
        return chatSessionMapper.toResponse(getSessionEntity(sessionId, userId));
    }

    @Transactional(readOnly = true)
    @Override
    public ChatSessionResponse findSessionByUUID(String uuid) {
        Long userId = securityService.getCurrentUser().getId();
        return chatSessionMapper.toResponse(
                chatSessionRepository.findByUuidAndUser_Id(uuid, userId)
                        .orElseThrow(() -> new EntityNotFoundException("Chat session not found: " + uuid))
        );
    }

    @Transactional(readOnly = true)
    @Override
    public List<ChatSessionResponse> findAllSessionsByUser(Long userId) {
        return chatSessionRepository.findAllByUser_Id(userId)
                .stream()
                .map(chatSessionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatSessionResponse> listSessionsByUser(Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ChatSession> page = chatSessionRepository.findAllByUser_Id(userId, stablePage);
        return PageResponseFactory.from(page, chatSessionMapper::toResponse);
    }

    @Override
    public boolean isSessionActive(Long sessionId, Long userId) {
        return getSessionEntity(sessionId, userId).getState() == ChatSessionState.ACTIVE;
    }










    @Transactional
    @Override
    @RequiresPlan(SubscriptionPlan.PREMIUM)
    public ChatMessageResponse sendMessage(Long sessionId, String rawUserPrompt) {
        User user = securityService.getCurrentUser();
        Long userId = user.getId();
        ChatSession session = getSessionEntity(sessionId, userId);
        ZoneId userZoneId = user.getTimeZone().toZoneId();

        if (session.getState() != ChatSessionState.ACTIVE) {
            throw new IllegalStateException("La sesion " + sessionId + " no está activa.");
        }

        String sanitized = AiUtils.sanitizeUserPrompt(rawUserPrompt, aiProperties.maxUserPromptLength());
        ChatSkill skill = AiUtils.detectSkill(sanitized);
        log.info("[CHAT] Skill detectada: {} para sesion: {}", skill, sessionId);

        List<ChatMessage> history = chatMessageRepository
                .findBySession_IdOrderByTurnNumberAsc(sessionId);

        int nextTurn = history.size() + 1;
        ChatMessage userMessage = buildUserMessage(sanitized, session, nextTurn, userZoneId);
        chatMessageRepository.save(userMessage);

        List<ChatMessageResponse> historyDtos = history.stream()
                .map(msg -> chatMessageMapper.toResponse(msg, user))
                .toList();

        ChatMessageRequest adapterRequest = ChatMessageRequest.builder()
                .sessionId(sessionId)
                .session(session)
                .promptRequest(new PromptRequest(
                        sanitized,
                        skill == ChatSkill.ADVISOR ? session.getFinancialContext() : null
                ))
                .skill(skill)
                .history(historyDtos)
                .build();

        ChatMessage assistantMessage = chatPort.sendMessage(adapterRequest);
        assistantMessage.setSession(session);
        assistantMessage.setTurnNumber(nextTurn + 1);
        chatMessageRepository.save(assistantMessage);

        chatSessionRepository.save(session);

        log.info("[CHAT] Respuesta persistida. Modelo: {}, Latencia: {} ms, InteractionId: {}, Tokens Usados: {}",
                assistantMessage.getModel(), assistantMessage.getLatencyMs(), session.getInteractionId(), assistantMessage.getTokensUsed());

        return chatMessageMapper.toResponse(assistantMessage, user);
    }




    private UserDataContext refreshUserDataContext(Long userId) {
        try {
            return userDataContextService.updateUserDataContext(userId);
        } catch (Exception e) {
            log.warn("No se pudo refrescar el contexto financiero del usuario {}; se usa el persistido", userId, e);
            return userDataContextService.getUserDataContext(userId);
        }
    }

    private String buildSessionContext(UserDataContext ctx) {
        if (ctx == null) return null;

        return """
                === PERIODO ACTUAL ===
                %s
                
                === PERIODO ANTERIOR ===
                %s
                """.formatted(
                ctx.getCurrentData() != null ? ctx.getCurrentData() : "Sin datos.",
                ctx.getPreviousData() != null ? ctx.getPreviousData() : "Sin datos."
        );
    }

    private ChatMessage buildUserMessage(
            String prompt, ChatSession session, int turnNumber, ZoneId userZoneId) {
        ChatMessage msg = new ChatMessage();
        msg.setRole(ChatRole.USER);
        msg.setMessage(prompt);
        msg.setTurnNumber(turnNumber);
        msg.setSession(session);
        msg.setCreatedAt(OffsetDateTime.now(userZoneId));
        return msg;
    }

    private ChatSession getSessionEntity(Long sessionId, Long userId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesion no encontrada: " + sessionId));
        if (!session.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("La sesion no pertenece al usuario: " + userId);
        }
        return session;
    }
}
