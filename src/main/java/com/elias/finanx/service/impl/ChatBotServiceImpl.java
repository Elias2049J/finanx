package com.elias.finanx.service.impl;

import com.elias.finanx.config.AiProperties;
import com.elias.finanx.dto.ai.ChatMessageRequest;
import com.elias.finanx.dto.ai.ChatMessageResponse;
import com.elias.finanx.dto.ai.ChatSessionResponse;
import com.elias.finanx.dto.ai.PromptRequest;
import com.elias.finanx.entity.ChatMessage;
import com.elias.finanx.entity.ChatSession;
import com.elias.finanx.entity.User;
import com.elias.finanx.entity.UserDataContext;
import com.elias.finanx.entity.enums.ChatRole;
import com.elias.finanx.entity.enums.ChatSessionState;
import com.elias.finanx.entity.enums.ChatSkill;
import com.elias.finanx.entity.enums.SubscriptionPlan;
import com.elias.finanx.mapper.ChatMessageMapper;
import com.elias.finanx.mapper.ChatSessionMapper;
import com.elias.finanx.plan.gating.PlanSubject;
import com.elias.finanx.plan.gating.RequiresPlan;
import com.elias.finanx.port.ChatPort;
import com.elias.finanx.repository.ChatMessageRepository;
import com.elias.finanx.repository.ChatSessionRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.ChatBotService;
import com.elias.finanx.service.UserDataContextService;
import com.elias.finanx.util.AiUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Servicio de negocio del chatbot financiero.
 * <ul>
 *   <li>Gestion del ciclo de vida de sesiones de chat.</li>
 *   <li>Sanitizacion y deteccion de skill del mensaje entrante.</li>
 *   <li>Inyeccion del contexto financiero cuando la skill es ADVISOR.</li>
 *   <li>Persistencia de mensajes usuario + asistente.</li>
 *   <li>Delegacion de la llamada al modelo vía {@link ChatPort}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBotServiceImpl implements ChatBotService {

    private final ChatPort chatPort;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final UserDataContextService userDataContextService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AiProperties aiProperties;

    @Transactional
    @Override
    @RequiresPlan(SubscriptionPlan.PREMIUM)
    public ChatSessionResponse startSession(@PlanSubject Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        UserDataContext dataContext = userDataContextService.getUserDataContext(userId);

        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setFinancialContext(buildSessionContext(dataContext));

        ChatSession saved = chatSessionRepository.save(session);
        log.info("[CHAT] Sesion iniciada: {} para usuario: {}", saved.getUuid(), userId);
        return chatSessionMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public ChatSessionResponse closeSession(Long sessionId, Long userId) {
        ChatSession session = getSessionEntity(sessionId, userId);
        session.closeSession();
        return chatSessionMapper.toResponse(chatSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    @Override
    public ChatSessionResponse findSessionById(Long sessionId, Long userId) {
        return chatSessionMapper.toResponse(getSessionEntity(sessionId, userId));
    }

    @Transactional(readOnly = true)
    @Override
    public ChatSessionResponse findSessionByUUID(String uuid) {
        return chatSessionMapper.toResponse(
                chatSessionRepository.findByUuid(uuid).orElseThrow()
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
    public boolean isSessionActive(Long sessionId, Long userId) {
        return getSessionEntity(sessionId, userId).getState() == ChatSessionState.ACTIVE;
    }

    /**
     * Procesa un mensaje del usuario dentro de una sesion activa:
     * <ol>
     *   <li>Sanitiza el prompt.</li>
     *   <li>Detecta la skill (ADVISOR / CHITCHAT).</li>
     *   <li>Persiste el mensaje del usuario.</li>
     *   <li>Construye el request con historial y contexto financiero.</li>
     *   <li>Llama al adapter de IA y persiste la respuesta.</li>
     * </ol>
     */
    @Transactional
    @Override
    @RequiresPlan(SubscriptionPlan.PREMIUM)
    public ChatMessageResponse sendMessage(Long sessionId, @PlanSubject Long userId, String rawUserPrompt) {
        ChatSession session = getSessionEntity(sessionId, userId);
        ZoneId userZoneId = userRepository.findById(userId).orElseThrow().getTimeZone().toZoneId();

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
                .map(chatMessageMapper::toResponse)
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

        return chatMessageMapper.toResponse(assistantMessage);
    }

    /**
     * Formatea el contexto financiero de dos periodos para inyeccion
     * en el system instruction de la skill ADVISOR.
     */
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