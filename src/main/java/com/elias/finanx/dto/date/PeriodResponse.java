package com.elias.finanx.dto.date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeriodResponse {
    private LocalDate start;
    private LocalDate end;
    private Long userId;
    private String readableString;
}

