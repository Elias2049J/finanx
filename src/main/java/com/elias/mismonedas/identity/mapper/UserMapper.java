package com.elias.mismonedas.identity.mapper;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.dto.user.UserRequest;
import com.elias.mismonedas.identity.dto.user.UserResponse;
import com.elias.mismonedas.shared.mapper.DateMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {DateMapper.class})
public interface UserMapper{

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(UserRequest dto);

    @Mapping(target = "fullName", expression = "java(entity.getFullName())")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    UserResponse toResponseWithContext(User entity, @Context User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "disabledAt", ignore = true)
    @Mapping(target = "preferences", ignore = true)
    void updateFromDto(UserRequest dto, @MappingTarget User entity);

    default UserResponse toResponse(User entity) {
        return toResponseWithContext(entity, entity);
    }
}
