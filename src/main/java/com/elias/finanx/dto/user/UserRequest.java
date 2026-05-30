package com.elias.finanx.dto.user;

import com.elias.finanx.entity.enums.TimeZone;
import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class UserRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String lastname;
    @Email
    @NotBlank
    private String email;
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    @NotNull
    private TimeZone timeZone;
}
