package com.elias.finanx.mapper;

import com.elias.finanx.dto.category.CategoryRequest;
import com.elias.finanx.dto.category.CategoryResponse;
import com.elias.finanx.entity.Category;
import org.mapstruct.*;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Mapper(componentModel = "spring")
public interface CategoryMapper{

    @Mapping(
            target = "disabledAt",
            expression = "java(mapOffsetToZoned(entity.getDisabledAt(), (entity.getUser() != null && entity.getUser().getTimeZone() != null) ? entity.getUser().getTimeZone().toZoneId() : null))"
    )
    CategoryResponse toResponse(Category entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    Category toEntity(CategoryRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateFromDto(CategoryRequest dto, @MappingTarget Category entity);

    default ZonedDateTime mapOffsetToZoned(OffsetDateTime value, ZoneId zoneId) {
        if (value == null || zoneId == null) {
            return null;
        }
        return value.atZoneSameInstant(zoneId);
    }
}
