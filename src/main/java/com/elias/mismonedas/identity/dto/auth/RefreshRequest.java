package com.elias.mismonedas.identity.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Refresh token a rotar o revocar")
public class RefreshRequest {

    @NotBlank
    @Schema(description = "Refresh token emitido por el servidor", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
