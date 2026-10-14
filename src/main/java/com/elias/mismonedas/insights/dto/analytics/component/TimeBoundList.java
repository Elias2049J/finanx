package com.elias.mismonedas.insights.dto.analytics.component;

import com.elias.mismonedas.shared.dto.date.PeriodResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TimeBoundList<T> {

    private PeriodResponse periodResponse;

    private List<T> items;

    private long count;
}

