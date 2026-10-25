package com.elias.mismonedas.identity.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload del ID Token de Google con informacion basica del usuario")
public record GoogleIdTokenPayload(

        @Schema(
                description = "Identificador inmutable de la cuenta de Google (claim sub)",
                example = "109876543210987654321"
        )
        String subject,

        @Schema(
                description = "Correo electronico del usuario, normalizado",
                example = "juan.perez@example.com"
        )
        String email,

        @Schema(
                description = "Nombre de pila del usuario",
                example = "Juan"
        )
        String givenName,

        @Schema(
                description = "Apellido del usuario",
                example = "Perez"
        )
        String familyName
) {
}
