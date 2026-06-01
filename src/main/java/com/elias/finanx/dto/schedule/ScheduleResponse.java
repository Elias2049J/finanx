package com.elias.finanx.dto.schedule;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleResponse;
import com.elias.finanx.entity.BudgetSchedule;
import com.elias.finanx.entity.enums.ScheduleState;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionScheduleResponse.class, name = "TRANSACTION"),
        @JsonSubTypes.Type(value = BudgetScheduleResponse.class, name = "BUDGET")
})
public class ScheduleResponse {
    private Long id;
    private Long userId;
    private Long categoryId;
    private String categoryName;
    private ZonedDateTime nextRunAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime lastRunAt;
    private Boolean active;
    private ScheduleState state;
    private RecurrenceRuleResponse recurrenceRule;
}
