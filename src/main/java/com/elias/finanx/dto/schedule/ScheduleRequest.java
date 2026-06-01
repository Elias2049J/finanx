package com.elias.finanx.dto.schedule;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.finanx.entity.BudgetSchedule;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import lombok.Data;
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionScheduleRequest.class, name = "TRANSACTION"),
        @JsonSubTypes.Type(value = BudgetScheduleRequest.class, name = "BUDGET")
})
@Data
public class ScheduleRequest {
    private Long userId;
    private Long categoryId;

    @Valid
    private RecurrenceRuleRequest recurrenceRule;
}
