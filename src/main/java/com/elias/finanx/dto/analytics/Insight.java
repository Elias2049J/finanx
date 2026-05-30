package com.elias.finanx.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
