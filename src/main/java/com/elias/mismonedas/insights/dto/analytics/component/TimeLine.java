package com.elias.mismonedas.insights.dto.analytics.component;

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

    private String dateRangeLabel;

    private String title;

    private List<TimeSeriesPoint<T>> points;

    private Aggregate aggregate;

    private long count;
}
