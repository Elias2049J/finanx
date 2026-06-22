package com.elias.finanx.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.TransactionType;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private Long id;
    private Long userId;
    private Long categoryId;
    private String categoryName;
    private Long reasonId;
    private String reasonDescription;
    private Long savingGoalId;
    private Long scheduleId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private TransactionType type;
    private LocalDateTime createdAt;
    private LocalDateTime disabledAt;
    private boolean active;
}
