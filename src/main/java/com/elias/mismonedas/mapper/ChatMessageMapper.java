package com.elias.mismonedas.mapper;

import com.elias.mismonedas.dto.ai.ChatMessageRequest;
import com.elias.mismonedas.dto.ai.ChatMessageResponse;
import com.elias.mismonedas.entity.ChatMessage;
import com.elias.mismonedas.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface ChatMessageMapper {

    @Mapping(source = "session.id", target = "sessionId")
    @Mapping(source = "session.uuid", target = "sessionUUID")
    @Mapping(target = "createdAt", qualifiedByName = "OffsetToLocal")
    ChatMessageResponse toResponse(ChatMessage entity, @Context User user);

    @Mapping(source = "promptRequest.userPrompt", target = "message")
    ChatMessage toEntity(ChatMessageRequest request);
}
