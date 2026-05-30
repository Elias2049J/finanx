package com.elias.finanx.dto.analytics;

import com.elias.finanx.entity.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryAggregate {
    private CategorySummary category;
    private TransactionType transactionType;

    private long occurrenceCount;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;

    private BigDecimal sharePercentage;
}