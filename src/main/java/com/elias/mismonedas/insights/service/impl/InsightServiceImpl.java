package com.elias.mismonedas.insights.service.impl;

import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.billing.plan.RequiresPlan;
import com.elias.mismonedas.chat.dto.ai.PromptRequest;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.insights.dto.analytics.component.InsightSummary;
import com.elias.mismonedas.insights.dto.insight.InsightRequest;
import com.elias.mismonedas.insights.dto.insight.InsightResponse;
import com.elias.mismonedas.insights.service.InsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightServiceImpl implements InsightService {

    @Override
    public InsightSummary getInsights(PromptRequest request, int count) throws HttpException, IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    @RequiresPlan(SubscriptionPlan.PREMIUM)
    public InsightResponse generateInsight(User user, InsightRequest request) {
        log.info("Generando insight premium para userId={}, query={}", user.getId(), request.getQuery());

        InsightSummary summary = new InsightSummary(List.of(), 0);
        return InsightResponse.builder()
                .summary(summary)
                .build();
    }
}
