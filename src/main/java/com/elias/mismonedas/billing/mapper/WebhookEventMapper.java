package com.elias.mismonedas.billing.mapper;

import com.elias.mismonedas.billing.domain.entity.WebhookEvent;
import com.elias.mismonedas.billing.dto.webhook.WebhookEventResponse;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.shared.mapper.DateMapper;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface WebhookEventMapper {

    @Mapping(target = "receivedAt", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "processedAt", qualifiedByName = "OffsetToLocal")
    WebhookEventResponse toResponse(WebhookEvent entity, @Context User user);
}
