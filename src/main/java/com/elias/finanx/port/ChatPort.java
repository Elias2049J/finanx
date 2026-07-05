package com.elias.finanx.port;

import com.elias.finanx.dto.ai.ChatMessageRequest;
import com.elias.finanx.entity.ChatMessage;

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