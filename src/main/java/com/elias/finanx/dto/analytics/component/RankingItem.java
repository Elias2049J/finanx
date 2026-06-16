package com.elias.finanx.dto.analytics.component;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RankingItem<F> {
    private AggregateBy<F> aggregate;
    private int rank;
    private BigDecimal metricValue;
}
