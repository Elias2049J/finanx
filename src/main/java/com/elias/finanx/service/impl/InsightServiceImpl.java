package com.elias.finanx.service.impl;

import com.elias.finanx.dto.ai.PromptRequest;
import com.elias.finanx.dto.analytics.component.InsightSummary;
import com.elias.finanx.port.InsightPort;
import com.elias.finanx.service.InsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpException;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightServiceImpl implements InsightService {
    private final InsightPort insightPort;

    @Override
    public InsightSummary getInsights(PromptRequest prompt, int count) throws HttpException, IOException {
        log.info("Generando {} insights. Prompt: {}", count, prompt.getUserPrompt());
        return insightPort.getInsights(prompt, count);
    }
}
