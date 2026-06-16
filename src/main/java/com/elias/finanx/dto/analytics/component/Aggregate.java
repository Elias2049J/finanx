package com.elias.finanx.dto.analytics.component;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aggregate {
    private String title;

    private BigDecimal totalAmount;
    private BigDecimal averageAmount;

    private long occurrenceCount;
    private BigDecimal max;
    private BigDecimal min;
}