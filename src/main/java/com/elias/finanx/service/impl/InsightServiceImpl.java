package com.elias.finanx.service.impl;

import com.elias.finanx.dto.ai.PromptRequest;
import com.elias.finanx.dto.analytics.component.Insight;
import com.elias.finanx.dto.analytics.component.InsightSummary;
import com.elias.finanx.dto.insight.InsightRequest;
import com.elias.finanx.dto.insight.InsightResponse;
import com.elias.finanx.entity.User;
import com.elias.finanx.entity.enums.SubscriptionPlan;
import com.elias.finanx.plan.gating.PlanSubject;
import com.elias.finanx.plan.gating.RequiresPlan;
import com.elias.finanx.service.InsightService;
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
    public InsightResponse generateInsight(@PlanSubject User user, InsightRequest request) {
        log.info("Generando insight premium para userId={}, query={}", user.getId(), request.getQuery());

        InsightSummary summary = new InsightSummary(List.of(), 0);
        return InsightResponse.builder()
                .summary(summary)
                .build();
    }
}
