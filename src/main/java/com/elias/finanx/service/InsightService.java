package com.elias.finanx.service;

import com.elias.finanx.dto.ai.PromptRequest;
import com.elias.finanx.dto.analytics.component.InsightSummary;
import org.apache.http.HttpException;

import java.io.IOException;

public interface InsightService {
    InsightSummary getInsights(PromptRequest request, int count) throws HttpException, IOException;
}
