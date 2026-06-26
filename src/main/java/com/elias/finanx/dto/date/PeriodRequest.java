package com.elias.finanx.dto.date;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Schema(description = "Solicitud que define un periodo de fechas asociado a un usuario")
public class PeriodRequest {

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(
            description = "Fecha de inicio del periodo (formato yyyy-MM-dd)",
            example = "2026-06-01"
    )
    private LocalDate start;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(
            description = "Fecha de fin del periodo (formato yyyy-MM-dd)",
            example = "2026-06-30"
    )
    private LocalDate end;

    @NotNull
    @Schema(
            description = "Identificador unico del usuario asociado al periodo",
            example = "42"
    )
    private Long userId;
}


