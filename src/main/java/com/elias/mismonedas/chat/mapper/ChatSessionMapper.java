package com.elias.mismonedas.chat.mapper;

import com.elias.mismonedas.chat.domain.entity.ChatSession;
import com.elias.mismonedas.chat.dto.ai.ChatSessionResponse;
import com.elias.mismonedas.chat.mapper.ChatMessageMapper;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.shared.mapper.DateMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {DateMapper.class, ChatMessageMapper.class})
public interface ChatSessionMapper {

    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "endedAt", target = "endedAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "messages", target = "history")
    ChatSessionResponse toResponseWithContext(ChatSession entity, @Context User user);

    default ChatSessionResponse toResponse(ChatSession entity) {
        return toResponseWithContext(entity, entity.getUser());
    }
}
