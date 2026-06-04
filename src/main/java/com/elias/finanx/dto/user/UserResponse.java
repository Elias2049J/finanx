package com.elias.finanx.dto.user;

import com.elias.finanx.entity.enums.Role;
import com.elias.finanx.entity.enums.TimeZone;
import com.elias.finanx.entity.enums.UserState;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserResponse {
    private Long id;
    private String name;
    private String lastname;
    private String email;
    private String username;
    private Role role;
    private UserState state;
    private BigDecimal moneyBalance;
    private LocalDateTime createdAt;
    private LocalDateTime disabledAt;

    private String fullName;
    private TimeZone timeZone;
}
