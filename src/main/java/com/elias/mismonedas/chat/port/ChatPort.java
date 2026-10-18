package com.elias.mismonedas.chat.port;

import com.elias.mismonedas.chat.domain.entity.ChatMessage;
import com.elias.mismonedas.chat.dto.ai.ChatMessageRequest;



public interface ChatPort {









    ChatMessage sendMessage(ChatMessageRequest request);
}