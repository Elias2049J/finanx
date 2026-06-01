package com.elias.finanx.dto.schedule;

import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.TransactionType;
import lombok.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionScheduleRequest extends ScheduleRequest {
    private Long reasonId;
    private String description;
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal amount;
    @NotNull
    private PaymentMethod paymentMethod;
    @NotNull
    private TransactionType transactionType;
}
