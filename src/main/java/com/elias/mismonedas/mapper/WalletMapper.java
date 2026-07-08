package com.elias.mismonedas.mapper;

import com.elias.mismonedas.dto.wallet.WalletRequest;
import com.elias.mismonedas.dto.wallet.WalletResponse;
import com.elias.mismonedas.entity.Wallet;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface WalletMapper {

    @Mapping(source = "user.id", target = "userId")
    WalletResponse toResponse(Wallet entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    Wallet toEntity(WalletRequest dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    void updateFromDto(WalletRequest dto, @MappingTarget Wallet entity);
}
