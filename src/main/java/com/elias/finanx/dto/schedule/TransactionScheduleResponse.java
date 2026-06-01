package com.elias.finanx.dto.schedule;

import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.TransactionType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionScheduleResponse extends ScheduleResponse {
    private Long reasonId;
    private String reasonDesc;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private TransactionType transactionType;
}
