package com.elias.mismonedas.catalog.mapper;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.dto.category.CategoryRequest;
import com.elias.mismonedas.catalog.dto.category.CategoryResponse;
import com.elias.mismonedas.catalog.dto.category.CategorySummary;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.shared.mapper.DateMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface CategoryMapper{

    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    CategoryResponse toResponseWithContext(Category entity, @Context User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    Category toEntity(CategoryRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateFromDto(CategoryRequest dto, @MappingTarget Category entity);

    @Mapping(source = "categoryName", target = "name")
    @Mapping(source = "categoryId", target = "id")
    @Mapping(source = "type", target = "type")
    CategorySummary toSummary(TransactionResponse response);

    default CategoryResponse toResponse(Category entity) {
        return toResponseWithContext(entity, entity.getUser());
    }
}
