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
public class AggregateBy<F> {
    private Aggregate aggregate;
    private F factor;
    private String factorName;
    private BigDecimal sharePercentage;
}
