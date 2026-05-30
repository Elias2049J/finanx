package com.elias.finanx.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsPeriod {
    private OffsetDateTime start;
    private OffsetDateTime end;
}

