package com.elias.mismonedas.mapper;

import com.elias.mismonedas.dto.payment.PaymentRequest;
import com.elias.mismonedas.dto.payment.PaymentResponse;
import com.elias.mismonedas.entity.Payment;
import com.elias.mismonedas.entity.User;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface PaymentMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "subscription.id", target = "subscriptionId")
    @Mapping(target = "paidAt", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(target = "updatedAt", qualifiedByName = "OffsetToLocal")
    PaymentResponse toResponseWithContext(Payment entity, @Context User user);

    default PaymentResponse toResponse(Payment entity) {
        return toResponseWithContext(entity, entity.getUser());
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "subscription", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "externalReference", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Payment toEntity(PaymentRequest request);
}
