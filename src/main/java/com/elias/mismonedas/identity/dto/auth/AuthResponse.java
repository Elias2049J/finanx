package com.elias.mismonedas.identity.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Par de tokens emitido tras autenticar: access token de vida corta y refresh token rotativo")
public class AuthResponse {

    @Schema(description = "Access token JWT de vida corta", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "Refresh token opaco; se rota y revoca en cada uso", example = "Kf3s7QwLmN2rT4vYbC6dE8hJkR0sU")
    private String refreshToken;

    @Schema(description = "Vida del access token en segundos", example = "900")
    private long expiresIn;
}
