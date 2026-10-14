package com.elias.mismonedas.insights.port;

import com.elias.mismonedas.chat.dto.ai.PromptRequest;
import com.elias.mismonedas.insights.dto.analytics.component.InsightSummary;
import org.apache.http.HttpException;

import java.io.IOException;

public interface InsightPort {
    InsightSummary getInsights(PromptRequest promptRequest, int insightsCount) throws HttpException, IOException;
}
