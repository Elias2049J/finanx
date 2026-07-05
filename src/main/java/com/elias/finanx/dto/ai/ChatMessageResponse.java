package com.elias.finanx.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.elias.finanx.entity.enums.ChatRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatHistoryResponse", description = "Representa un turno dentro de la sesion de chat")
public class ChatMessageResponse {

    @Schema(description = "Identificador unico del historial", example = "10")
    private Long id;

    @Schema(description = "Numero de turno dentro de la sesion", example = "1")
    private Integer turnNumber;

    @Schema(description = "Rol del mensaje", example = "USER")
    private ChatRole role;

    @Schema(description = "Contenido del mensaje", example = "Hola, necesito ayuda")
    private String message;

    @Schema(description = "Cantidad de tokens usados", example = "120")
    private Integer tokensUsed;

    @Schema(description = "Modelo utilizado", example = "gpt-4")
    private String model;

    @Schema(description = "Motivo de finalizacion", example = "stop")
    private String finishReason;

    @Schema(description = "Ratings de seguridad en formato JSON")
    private Map<String, Object> safetyRatings;

    @Schema(description = "Latencia en milisegundos", example = "250")
    private Long latencyMs;

    @Schema(description = "Metadatos adicionales en formato JSON")
    private Map<String, Object> metadata;

    @Schema(description = "Fecha de creacion del mensaje", example = "2026-06-26T20:38:00Z")
    private LocalDateTime createdAt;

    @Schema(description = "UUID de la sesion")
    private String sessionUUID;

    @Schema(description = "ID de la sesion")
    private Long sessionId;

}

