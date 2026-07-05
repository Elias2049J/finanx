package com.elias.finanx.mapper;

import com.elias.finanx.dto.ai.ChatMessageRequest;
import com.elias.finanx.dto.ai.ChatMessageResponse;
import com.elias.finanx.entity.ChatMessage;
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
