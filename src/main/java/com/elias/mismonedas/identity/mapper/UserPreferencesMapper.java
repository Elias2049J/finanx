package com.elias.mismonedas.identity.mapper;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.dto.user.UserPreferencesRequest;
import com.elias.mismonedas.identity.dto.user.UserPreferencesResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserPreferencesMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserPreferences toEntity(UserPreferencesRequest request);

    UserPreferencesResponse toResponse(UserPreferences entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromDto(UserPreferencesRequest dto, @MappingTarget UserPreferences entity);
}
