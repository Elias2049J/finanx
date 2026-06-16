package com.elias.finanx.dto.analytics;

import com.elias.finanx.dto.category.CategoryResponse;
import com.elias.finanx.dto.reason.ReasonResponse;
import com.elias.finanx.entity.enums.RecurrenceType;
import com.elias.finanx.entity.enums.TransactionType;
import lombok.*;

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