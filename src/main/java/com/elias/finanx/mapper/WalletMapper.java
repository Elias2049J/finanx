package com.elias.finanx.mapper;

import com.elias.finanx.dto.wallet.WalletRequest;
import com.elias.finanx.dto.wallet.WalletResponse;
import com.elias.finanx.entity.Wallet;
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
