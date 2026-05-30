package com.elias.finanx.dto.scheduledtransaction;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.finanx.dto.transaction.TransactionRequest;
import com.elias.finanx.entity.enums.TimeZone;
import jakarta.validation.Valid;
import lombok.Data;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

@Data
public class TransactionScheduleRequest {
    @NotNull
    @Valid
    private TransactionRequest transaction;
    @NotNull
    private LocalDateTime nextRunAt;
    @NotNull
    private LocalDateTime endAt;

    @Valid
    private RecurrenceRuleRequest recurrenceRule;
}
