package com.elias.finanx.dto.user;

import com.elias.finanx.entity.enums.Role;
import com.elias.finanx.entity.enums.TimeZone;
import com.elias.finanx.entity.enums.UserState;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class UserResponseDTO {
    private Long id;
    private String name;
    private String lastname;
    private String email;
    private String username;
    private Role role;
    private UserState state;
    private BigDecimal moneyBalance;
    private OffsetDateTime createdAt;

    private String fullName;
    private TimeZone timeZone;
}
