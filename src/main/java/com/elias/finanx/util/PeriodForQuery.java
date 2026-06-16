package com.elias.finanx.util;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

@AllArgsConstructor
@Data
public class PeriodForQuery {
    private OffsetDateTime start;
    private OffsetDateTime end;
    private Long userId;
}
