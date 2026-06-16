package com.elias.finanx.dto.analytics.component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightSummary {
    private List<Insight> insights;
    private int insightsCount;
}
