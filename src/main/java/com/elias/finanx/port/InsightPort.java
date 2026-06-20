package com.elias.finanx.port;

import com.elias.finanx.dto.ai.PromptRequest;
import com.elias.finanx.dto.analytics.component.InsightSummary;
import org.apache.http.HttpException;

import java.io.IOException;

public interface InsightPort {
    InsightSummary getInsights(PromptRequest promptRequest, int insightsCount) throws HttpException, IOException;
}
