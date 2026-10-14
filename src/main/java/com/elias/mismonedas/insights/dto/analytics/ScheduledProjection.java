package com.elias.mismonedas.insights.dto.analytics;

import com.elias.mismonedas.catalog.dto.category.CategoryResponse;
import com.elias.mismonedas.catalog.dto.reason.ReasonResponse;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.scheduling.domain.enums.RecurrenceType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledProjection {
    private Long scheduledTransactionId;
    private TransactionType transactionType;
    private BigDecimal transactionAmount;

    private CategoryResponse category;
    private ReasonResponse reason;

    private RecurrenceType recurrenceType;
    private LocalDateTime nextRunAt;
    private LocalDateTime endAt;

    private int expectedOccurrenceCount;
    private BigDecimal expectedTotalAmount;
}