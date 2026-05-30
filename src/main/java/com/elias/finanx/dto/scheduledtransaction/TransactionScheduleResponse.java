package com.elias.finanx.dto.scheduledtransaction;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleResponse;
import com.elias.finanx.dto.transaction.TransactionResponse;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class TransactionScheduleResponse {
    private Long id;
    private TransactionResponse transaction;
    private ZonedDateTime nextRunAt;
    private ZonedDateTime endAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime lastRunAt;
    private Boolean active;

    private RecurrenceRuleResponse recurrenceRule;
}
