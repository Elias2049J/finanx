package com.elias.finanx.dto.analytics.component;

import com.elias.finanx.dto.date.PeriodResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TimeLine<T> {
    private PeriodResponse periodResponse;
    private String title;
    private List<TimeSeriesPoint<T>> points;
    private Aggregate aggregate;
    private long count;
}
