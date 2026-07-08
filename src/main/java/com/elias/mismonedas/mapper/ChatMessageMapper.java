package com.elias.mismonedas.mapper;

import com.elias.mismonedas.dto.ai.ChatMessageRequest;
import com.elias.mismonedas.dto.ai.ChatMessageResponse;
import com.elias.mismonedas.entity.ChatMessage;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface ChatMessageMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "session.id", target = "sessionId")
    @Mapping(source = "session.uuid", target = "sessionUUID")
    ChatMessageResponse toResponse(ChatMessage entity);

    @Mapping(source = "promptRequest.userPrompt", target = "message")
    ChatMessage toEntity(ChatMessageRequest request);
}
