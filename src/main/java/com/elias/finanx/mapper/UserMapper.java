package com.elias.finanx.mapper;

import com.elias.finanx.dto.user.UserRequest;
import com.elias.finanx.dto.user.UserResponseDTO;
import com.elias.finanx.entity.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserMapper{

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "moneyBalance", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(UserRequest dto);

    @Mapping(target = "fullName", expression = "java(entity.getFullName())")
    UserResponseDTO toResponse(User entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "moneyBalance", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateFromDto(UserRequest dto, @MappingTarget User entity);
}

