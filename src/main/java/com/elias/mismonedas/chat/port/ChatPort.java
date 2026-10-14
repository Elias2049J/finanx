package com.elias.mismonedas.chat.port;

import com.elias.mismonedas.chat.domain.entity.ChatMessage;
import com.elias.mismonedas.chat.dto.ai.ChatMessageRequest;

/**
 * Puerto de salida hacia el proveedor de IA conversacional.
 */
public interface ChatPort {

    /**
     * Envia un mensaje dentro de una sesion de chat y retorna la respuesta
     * del asistente como entidad para persistir.
     *
     * @param request contiene el prompt del usuario, el contexto financiero
     *                (si aplica) y el historial de la sesion activa.
     * @return {@link ChatMessage} con el mensaje del asistente y todos los
     *         metadatos de la llamada (modelo usado, latencia, tokens, etc.).
     */
    ChatMessage sendMessage(ChatMessageRequest request);
}