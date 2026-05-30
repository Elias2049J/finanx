package com.elias.finanx.mapper;

import com.elias.finanx.dto.category.CategoryRequest;
import com.elias.finanx.dto.category.CategoryResponse;
import com.elias.finanx.entity.Category;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface CategoryMapper{

    CategoryResponse toResponse(Category entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    Category toEntity(CategoryRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateFromDto(CategoryRequest dto, @MappingTarget Category entity);
}
