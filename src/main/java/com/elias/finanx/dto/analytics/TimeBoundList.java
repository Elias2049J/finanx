package com.elias.finanx.dto.analytics;

import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.dto.date.PeriodResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class TimeBoundList<T> {
    private PeriodResponse periodResponse;
    private List<T> items;
    private long count;
}
