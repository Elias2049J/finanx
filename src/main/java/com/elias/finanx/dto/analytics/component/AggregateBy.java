package com.elias.finanx.dto.analytics.component;

import io.swagger.v3.oas.annotations.media.Schema;
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
