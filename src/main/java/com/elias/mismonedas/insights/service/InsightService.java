package com.elias.mismonedas.insights.service;

import com.elias.mismonedas.chat.dto.ai.PromptRequest;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.insights.dto.analytics.component.InsightSummary;
import com.elias.mismonedas.insights.dto.insight.InsightRequest;
import com.elias.mismonedas.insights.dto.insight.InsightResponse;
import org.apache.http.HttpException;

import java.io.IOException;

public interface InsightService {
    InsightSummary getInsights(PromptRequest request, int count) throws HttpException, IOException;

    InsightResponse generateInsight(User user, InsightRequest request);
}
