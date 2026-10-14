package com.elias.mismonedas.insights.dto.analytics.component;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
