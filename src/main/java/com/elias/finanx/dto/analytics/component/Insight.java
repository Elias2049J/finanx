package com.elias.finanx.dto.analytics.component;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Insight {
    private InsightSeverity severity;
    private String title;
    private String description;
    private String action;

    public enum InsightSeverity {
        INFO,
        WARNING,
        CRITICAL
    }
}
