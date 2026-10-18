package com.elias.mismonedas.chat.dto.ai;

import com.elias.mismonedas.chat.domain.entity.ChatSession;
import com.elias.mismonedas.chat.domain.enums.ChatSkill;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;









@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatMessageRequest", description = "Solicitud de mensaje al chatbot financiero")
public class ChatMessageRequest {

    @NotNull
    @Schema(description = "ID de la sesion activa", example = "1")
    private Long sessionId;





    @NotNull
    @Schema(description = "Sesion activa con interactionId de Gemini")
    private ChatSession session;

    @NotNull
    @Schema(description = "Prompt del usuario ya sanitizado")
    private PromptRequest promptRequest;

    @Schema(description = "Skill detectada para esta interaccion")
    private ChatSkill skill;





    @Schema(description = "Historial local — solo para fallback si Gemini expira el contexto")
    private List<ChatMessageResponse> history;
}