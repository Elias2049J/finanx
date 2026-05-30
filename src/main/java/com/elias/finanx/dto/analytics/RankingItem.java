package com.elias.finanx.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RankingItem<T> {
    private int rank;
    private T item;

    private BigDecimal metricValue;

    private long occurrenceCount;

    private BigDecimal sharePercentage;
}
