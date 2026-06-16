package com.elias.finanx.dto.analytics.component;

import lombok.*;
import lombok.experimental.SuperBuilder;

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
