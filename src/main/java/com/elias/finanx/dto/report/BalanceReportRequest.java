package com.elias.finanx.dto.report;

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
public class BalanceReportRequest {
    @NotNull
    private LocalDate from;

    @NotNull
    private LocalDate to;

    @NotNull
    private Long userId;
}

