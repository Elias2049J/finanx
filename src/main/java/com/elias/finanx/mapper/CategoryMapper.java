package com.elias.finanx.mapper;

import com.elias.finanx.dto.category.CategoryRequest;
import com.elias.finanx.dto.category.CategoryResponse;
import com.elias.finanx.dto.category.CategorySummary;
import com.elias.finanx.dto.transaction.TransactionResponse;
import com.elias.finanx.entity.Category;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface CategoryMapper{

    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    CategoryResponse toResponse(Category entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    Category toEntity(CategoryRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateFromDto(CategoryRequest dto, @MappingTarget Category entity);

    @Mapping(source = "response.categoryName", target = "id")
    @Mapping(source = "response.categoryId", target = "name")
    CategorySummary toSummary(TransactionResponse response);
}
