package com.elias.mismonedas.mapper;

import com.elias.mismonedas.dto.user.UserPreferencesRequest;
import com.elias.mismonedas.dto.user.UserPreferencesResponse;
import com.elias.mismonedas.entity.FcmToken;
import com.elias.mismonedas.entity.UserPreferences;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserPreferencesMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserPreferences toEntity(UserPreferencesRequest request);

    @Mapping(target = "fcmTokens", expression = "java(toTokenList(entity))")
    UserPreferencesResponse toResponse(UserPreferences entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromDto(UserPreferencesRequest dto, @MappingTarget UserPreferences entity);

    default List<String> toTokenList(UserPreferences entity) {
        var user = entity == null ? null : entity.getUser();
        if (user == null || user.getFcmTokens() == null) return List.of();
        return user.getFcmTokens().stream()
                .map(FcmToken::getToken)
                .toList();
    }
}
