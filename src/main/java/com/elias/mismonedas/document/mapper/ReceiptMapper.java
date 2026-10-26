package com.elias.mismonedas.document.mapper;

import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.dto.ReceiptResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReceiptMapper {
    @Mapping(source = "id", target = "receiptId")
    @Mapping(source = "image.id", target = "imageId")
    @Mapping(target = "improved", expression = "java(entity.isImproved())")
    @Mapping(target = "requiresReview", expression = "java(entity.requiresReview())")
    ReceiptResponse toResponse(Receipt entity);
}
