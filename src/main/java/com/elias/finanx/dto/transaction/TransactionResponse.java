package com.elias.finanx.dto.transaction;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.TransactionType;

@Data
public class TransactionResponse {
    private Long id;
    private Long userId;
    private Long categoryId;
    private Long categoryName;
    private Long reasonId;
    private Long reasonDescription;
    private Long savingGoalId;
    private Long scheduleId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private TransactionType type;
    private LocalDateTime createdAt;
    private LocalDateTime disabledAt;
    private boolean active;
}
