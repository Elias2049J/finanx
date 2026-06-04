package com.elias.finanx.mapper;

import com.elias.finanx.dto.user.UserRequest;
import com.elias.finanx.dto.user.UserResponse;
import com.elias.finanx.entity.User;
import com.elias.finanx.util.DateUtil;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = DateUtil.class)
public interface UserMapper{

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "moneyBalance", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(UserRequest dto);

    @Mapping(target = "fullName", expression = "java(entity.getFullName())")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "OffsetToLocal")
    @Mapping(source = "disabledAt", target = "disabledAt", qualifiedByName = "OffsetToLocal")
    UserResponse toResponse(User entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "moneyBalance", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "disabledAt", ignore = true)
    void updateFromDto(UserRequest dto, @MappingTarget User entity);
}

