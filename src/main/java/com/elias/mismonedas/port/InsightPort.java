package com.elias.mismonedas.port;

import com.elias.mismonedas.dto.ai.PromptRequest;
import com.elias.mismonedas.dto.analytics.component.InsightSummary;
import org.apache.http.HttpException;

import java.io.IOException;

public interface InsightPort {
    InsightSummary getInsights(PromptRequest promptRequest, int insightsCount) throws HttpException, IOException;
}
