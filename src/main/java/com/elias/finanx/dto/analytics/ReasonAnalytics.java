package com.elias.finanx.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReasonAnalytics {
    private Long userId;
    private AnalyticsPeriod period;
    private List<RankingItem<ReasonSummary>> topReasons;
}
