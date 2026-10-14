package com.elias.mismonedas.chat.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.elias.mismonedas.chat.domain.enums.ChatSessionState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatSessionResponse", description = "Representa una sesion de chat")
public class ChatSessionResponse {

    @Schema(description = "ID de la sesion", example = "1")
    private Long id;

    @Schema(description = "UUID de la sesion", example = "550e8400-e29b-41d4-a716-446655440000")
    private String uuid;

    @Schema(description = "Fecha de creacion de la sesion", example = "2026-06-26T20:38:00Z")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de finalizacion de la sesion", example = "2026-06-26T21:00:00Z")
    private LocalDateTime endedAt;

    @Schema(description = "Estado de la sesion", example = "ACTIVE")
    private ChatSessionState state;

    @Schema(description = "ID de usuario asociado a la sesion")
    private Long userId;

    @Schema(description = "Historial de mensajes de la sesion")
    private List<ChatMessageResponse> history;
}

