package com.elias.mismonedas.dto.ai;

import com.elias.mismonedas.entity.ChatSession;
import com.elias.mismonedas.entity.enums.ChatSkill;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request que viaja desde el servicio de negocio hasta el adapter de IA.
 *
 * <p>Con la Interactions API, el adapter ya no necesita el {@code history}
 * completo para enviarlo a Gemini: Google lo gestiona server-side a traves
 * del {@code interactionId} que vive en la {@link ChatSession}.
 * El {@code history} se mantiene para el fallback de reconstruccion cuando
 * Google expira el contexto (TTL 1 dia en free tier).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatMessageRequest", description = "Solicitud de mensaje al chatbot financiero")
public class ChatMessageRequest {

    @NotNull
    @Schema(description = "ID de la sesion activa", example = "1")
    private Long sessionId;

    /**
     * Sesion hidratada con su {@code interactionId} vigente.
     * El adapter la actualiza con el nuevo ID tras cada turno exitoso.
     * El servicio de negocio persiste la sesion despues de recibir el ChatMessage.
     */
    @NotNull
    @Schema(description = "Sesion activa con interactionId de Gemini")
    private ChatSession session;

    @NotNull
    @Schema(description = "Prompt del usuario ya sanitizado")
    private PromptRequest promptRequest;

    @Schema(description = "Skill detectada para esta interaccion")
    private ChatSkill skill;

    /**
     * Historial de mensajes de la sesion.
     * No se envia a Gemini en condiciones normales (lo gestiona Google server-side).
     * Se usa unicamente como fallback si el interactionId ha expirado.
     */
    @Schema(description = "Historial local — solo para fallback si Gemini expira el contexto")
    private List<ChatMessageResponse> history;
}