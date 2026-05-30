package com.elias.finanx.dto.analytics;

import com.elias.finanx.entity.enums.RecurrenceType;
import com.elias.finanx.entity.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledProjection {
    private Long scheduledTransactionId;
    private TransactionType transactionType;
    private BigDecimal transactionAmount;

    private CategorySummary category;
    private ReasonSummary reason;

    private RecurrenceType recurrenceType;
    private OffsetDateTime nextRunAt;
    private OffsetDateTime endAt;

    private int expectedOccurrenceCount;
    private BigDecimal expectedTotalAmount;
}