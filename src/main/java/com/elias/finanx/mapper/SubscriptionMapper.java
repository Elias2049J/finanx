package com.elias.finanx.mapper;

import com.elias.finanx.dto.subscription.SubscriptionRequest;
import com.elias.finanx.dto.subscription.SubscriptionResponse;
import com.elias.finanx.entity.Subscription;
import com.elias.finanx.entity.User;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface SubscriptionMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(target = "currentPeriodStart", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "currentPeriodEnd", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "canceledAt", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "updatedAt", qualifiedByName = "OffsetToLocal")
    SubscriptionResponse toResponseWithContext(Subscription entity, @Context User user);

    default SubscriptionResponse toResponse(Subscription entity) {
        return toResponseWithContext(entity, entity.getUser());
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "currentPeriodStart", ignore = true)
    @Mapping(target = "currentPeriodEnd", ignore = true)
    @Mapping(target = "canceledAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "payments", ignore = true)
    Subscription toEntity(SubscriptionRequest request);
}
