package com.elias.finanx.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReasonSummary {
    private Long reasonId;
    private String reasonDescription;
}