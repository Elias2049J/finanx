package com.elias.mismonedas.document.mapper;

import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.dto.ReceiptResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReceiptMapper {
    @Mapping(source = "id", target = "receiptId")
    @Mapping(source = "image.id", target = "imageId")
    ReceiptResponse toResponse(Receipt entity);
}
