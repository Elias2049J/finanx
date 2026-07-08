package com.elias.mismonedas.dto.analytics.component;

import lombok.*;

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
