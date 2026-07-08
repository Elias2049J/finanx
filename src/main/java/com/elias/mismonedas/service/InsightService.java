package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.ai.PromptRequest;
import com.elias.mismonedas.dto.analytics.component.InsightSummary;
import com.elias.mismonedas.dto.insight.InsightRequest;
import com.elias.mismonedas.dto.insight.InsightResponse;
import com.elias.mismonedas.entity.User;
import org.apache.http.HttpException;

import java.io.IOException;

public interface InsightService {
    InsightSummary getInsights(PromptRequest request, int count) throws HttpException, IOException;

    InsightResponse generateInsight(User user, InsightRequest request);
}
