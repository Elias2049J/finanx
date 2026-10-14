package com.elias.mismonedas.shared.dto.date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta que representa un periodo de fechas asociado a un usuario, incluyendo una version legible en texto")
public class PeriodResponse {

    @Schema(
            description = "Fecha de inicio del periodo (formato yyyy-MM-dd)",
            example = "2026-06-01"
    )
    private LocalDate start;

    @Schema(
            description = "Fecha de fin del periodo (formato yyyy-MM-dd)",
            example = "2026-06-30"
    )
    private LocalDate end;

    @Schema(
            description = "Identificador unico del usuario asociado al periodo",
            example = "42"
    )
    private Long userId;

    @Schema(
            description = "Representacion legible del periodo en texto",
            example = "01 junio 2026 a 30 junio 2026"
    )
    private String readableString;
}


