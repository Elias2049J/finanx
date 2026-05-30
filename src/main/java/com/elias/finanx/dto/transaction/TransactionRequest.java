package com.elias.finanx.dto.transaction;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.TransactionType;

@Data
public class TransactionRequest {
    @NotNull
    private Long userId;
    @NotNull
    private Long categoryId;
    private Long reasonId;
    private String description;
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal amount;
    @NotNull
    private PaymentMethod paymentMethod;
    @NotNull
    private TransactionType type;
}
