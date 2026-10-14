package com.elias.mismonedas.chat.mapper;

import com.elias.mismonedas.chat.domain.entity.ChatMessage;
import com.elias.mismonedas.chat.dto.ai.ChatMessageRequest;
import com.elias.mismonedas.chat.dto.ai.ChatMessageResponse;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.shared.mapper.DateMapper;
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
