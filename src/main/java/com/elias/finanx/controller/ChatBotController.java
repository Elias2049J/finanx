package com.elias.finanx.controller;

import com.elias.finanx.dto.ai.ChatMessageResponse;
import com.elias.finanx.dto.ai.ChatSessionResponse;
import com.elias.finanx.dto.ai.SendMessageRequest;
import com.elias.finanx.service.ChatBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/chat")
@RequiredArgsConstructor
@Tag(name = "ChatBot", description = "Chatbot financiero conversacional de Finanx")
public class ChatBotController {

    private final ChatBotService chatBotService;

    @Operation(
            summary = "Iniciar sesion de chat",
            description = "Crea una nueva sesion de chat e inyecta el contexto financiero del periodo actual y anterior."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion creada exitosamente"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @PostMapping("/sessions")
    public ResponseEntity<ChatSessionResponse> startSession(
            @Parameter(description = "ID del usuario", example = "1", required = true)
            @PathVariable Long userId) {
        return ResponseEntity.ok(chatBotService.startSession(userId));
    }

    @Operation(
            summary = "Cerrar sesion de chat",
            description = "Marca la sesion como cerrada. No se podran enviar mas mensajes en ella."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion cerrada exitosamente"),
            @ApiResponse(responseCode = "404", description = "Sesion no encontrada")
    })
    @PatchMapping("/sessions/{sessionId}/close")
    public ResponseEntity<ChatSessionResponse> closeSession(
            @Parameter(description = "ID del usuario", example = "1", required = true)
            @PathVariable Long userId,
            @Parameter(description = "ID de la sesion", example = "1", required = true)
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(chatBotService.closeSession(sessionId, userId));
    }

    @Operation(
            summary = "Obtener sesion por ID",
            description = "Retorna el detalle de una sesion incluyendo su historial de mensajes."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion encontrada"),
            @ApiResponse(responseCode = "404", description = "Sesion no encontrada")
    })
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ChatSessionResponse> findSessionById(
            @Parameter(description = "ID del usuario", example = "1", required = true)
            @PathVariable Long userId,
            @Parameter(description = "ID de la sesion", example = "1", required = true)
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(chatBotService.findSessionById(sessionId, userId));
    }

    @Operation(
            summary = "Obtener sesion por UUID",
            description = "Retorna el detalle de una sesion usando su UUID publico."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesion encontrada"),
            @ApiResponse(responseCode = "404", description = "Sesion no encontrada")
    })
    @GetMapping("/sessions/uuid/{uuid}")
    public ResponseEntity<ChatSessionResponse> findSessionByUUID(
            @Parameter(description = "ID del usuario", example = "1", required = true)
            @PathVariable Long userId,
            @Parameter(description = "UUID de la sesion", example = "550e8400-e29b-41d4-a716-446655440000", required = true)
            @PathVariable String uuid) {
        return ResponseEntity.ok(chatBotService.findSessionByUUID(uuid));
    }

    @Operation(
            summary = "Listar sesiones del usuario",
            description = "Retorna todas las sesiones de chat del usuario."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponse>> findAllSessionsByUser(
            @Parameter(description = "ID del usuario", example = "1", required = true)
            @PathVariable Long userId) {
        return ResponseEntity.ok(chatBotService.findAllSessionsByUser(userId));
    }

    @Operation(
            summary = "Enviar mensaje al chatbot",
            description = """
                    Envia un mensaje dentro de una sesion activa. El chatbot detecta automaticamente
                    la skill apropiada (ADVISOR para consultas financieras, CHITCHAT para conversacion general)
                    e inyecta el contexto financiero cuando corresponde.
                    Retorna la respuesta del asistente lista para mostrar al usuario.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensaje procesado y respuesta generada"),
            @ApiResponse(responseCode = "400", description = "Mensaje vacio o invalido"),
            @ApiResponse(responseCode = "404", description = "Sesion no encontrada"),
            @ApiResponse(responseCode = "409", description = "La sesion no esta activa")
    })
    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Parameter(description = "ID del usuario", example = "1", required = true)
            @PathVariable Long userId,
            @Parameter(description = "ID de la sesion activa", example = "1", required = true)
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(chatBotService.sendMessage(sessionId, userId, request.getMessage()));
    }
}