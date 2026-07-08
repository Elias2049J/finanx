package com.elias.finanx.mapper;

import com.elias.finanx.dto.webhook.WebhookEventResponse;
import com.elias.finanx.entity.User;
import com.elias.finanx.entity.WebhookEvent;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface WebhookEventMapper {

    @Mapping(target = "receivedAt", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "processedAt", qualifiedByName = "OffsetToLocal")
    WebhookEventResponse toResponse(WebhookEvent entity, @Context User user);
}
