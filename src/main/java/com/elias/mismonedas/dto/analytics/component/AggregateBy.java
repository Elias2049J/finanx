package com.elias.mismonedas.dto.analytics.component;

import lombok.*;

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
