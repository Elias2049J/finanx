package com.elias.finanx.service;

import com.elias.finanx.dto.ai.PromptRequest;
import com.elias.finanx.dto.analytics.component.InsightSummary;
import com.elias.finanx.dto.insight.InsightRequest;
import com.elias.finanx.dto.insight.InsightResponse;
import com.elias.finanx.entity.User;
import org.apache.http.HttpException;

import java.io.IOException;

public interface InsightService {
    InsightSummary getInsights(PromptRequest request, int count) throws HttpException, IOException;

    InsightResponse generateInsight(User user, InsightRequest request);
}
