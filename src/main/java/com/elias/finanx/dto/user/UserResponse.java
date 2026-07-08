package com.elias.finanx.dto.user;

import com.elias.finanx.entity.enums.Role;
import com.elias.finanx.entity.enums.UserState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta que representa un usuario registrado en el sistema")
public class UserResponse {

    @Schema(description = "Identificador unico del usuario", example = "42")
    private Long id;

    @Schema(description = "Nombres del usuario", example = "Jorge Jackson")
    private String name;

    @Schema(description = "Apellidos del usuario", example = "Valdivia Torreon")
    private String lastname;

    @Schema(description = "Correo electronico del usuario", example = "jorge.valdivia@example.com")
    private String email;

    @Schema(description = "Nombre de usuario unico", example = "UsuarioFin2026")
    private String username;

    @Schema(description = "Rol asignado al usuario", implementation = Role.class, example = "USER")
    private Role role;

    @Schema(description = "Estado actual del usuario", implementation = UserState.class, example = "ENABLED")
    private UserState state;

    @Schema(description = "Balance monetario del usuario", example = "1500.00")
    private BigDecimal moneyBalance;

    @Schema(description = "Fecha y hora de creacion del usuario", example = "2026-06-25T23:59:59")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha y hora en que el usuario fue deshabilitado, si aplica", example = "2026-07-01T12:00:00")
    private LocalDateTime disabledAt;

    @Schema(description = "Nombre completo del usuario en formato concatenado", example = "JORGE JACKSON VALDIVIA TORREON")
    private String fullName;

    @Schema(description = "Preferencias del usuario")
    private UserPreferencesResponse preferences;
}

