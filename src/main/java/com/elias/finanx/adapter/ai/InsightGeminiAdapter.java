package com.elias.finanx.adapter.ai;

import com.elias.finanx.dto.ai.PromptRequest;
import com.elias.finanx.dto.analytics.component.Insight;
import com.elias.finanx.dto.analytics.component.InsightSummary;
import com.elias.finanx.port.InsightPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InsightGeminiAdapter implements InsightPort {

    @Value("${app.gemini.model}")
    private String geminiModel;

    private final Client client;
    private final ObjectMapper objectMapper;

    @Override
    public InsightSummary getInsights(PromptRequest promptRequest, int count)
            throws HttpException, IOException {
        String prompt = buildPrompt(promptRequest, count);
        GenerateContentResponse response = client.models
                .generateContent(geminiModel, prompt, getConfig());
        return mapToInsightSummary(response);
    }

    private GenerateContentConfig getConfig() {
        return GenerateContentConfig.builder()
                .temperature(0.3f)
                .maxOutputTokens(2048)
                .responseMimeType("application/json")
                .build();
    }

    private String buildPrompt(PromptRequest request, int count) {
        return """
                Eres un analizador financiero personal. Analiza los datos financieros del usuario
                y responde a su consulta con insights concretos basados en los números del contexto.

                Consulta del usuario: %s

                Datos financieros:
                %s

                Reglas:
                - Genera exactamente %d insights
                - Usa CRITICAL solo si hay anomalía real (gasto > 80%% del ingreso, déficit, concentración > 70%%)
                - Usa WARNING para patrones de riesgo moderado (concentración > 50%%, ratio gasto/ingreso > 60%%)
                - Usa INFO para observaciones positivas o neutras
                - Cada insight debe referenciar números concretos del contexto
                - El campo "action" debe ser una acción específica y accionable, no genérica

                Devuelve ÚNICAMENTE este JSON, sin texto adicional ni backticks:
                {
                  "insightsCount": %d,
                  "insights": [
                    {
                      "severity": "INFO",
                      "title": "string (máx 60 caracteres)",
                      "description": "string (menciona cifras del contexto)",
                      "action": "string (acción concreta)"
                    }
                  ]
                }
                """.formatted(
                request.getUserPrompt(),
                request.getContext(),
                count,
                count
        );
    }

    private InsightSummary mapToInsightSummary(GenerateContentResponse response) {
        try {
            String json = response.text();

            if (json == null || json.isBlank()) {
                log.warn("Gemini devolvió respuesta vacía");
                return fallbackSummary();
            }

            InsightSummary summary = objectMapper.readValue(json, InsightSummary.class);
            normalizeSeverities(summary);
            return summary;

        } catch (Exception e) {
            log.error("Error deserializando respuesta de Gemini: {}", e.getMessage());
            return fallbackSummary();
        }
    }

    private InsightSummary fallbackSummary() {
        InsightSummary summary = new InsightSummary();
        summary.setInsightsCount(1);
        summary.setInsights(List.of(
                Insight.builder()
                        .severity(Insight.InsightSeverity.INFO)
                        .title("No se pudieron generar insights")
                        .description("El análisis no estuvo disponible en este momento.")
                        .action("Intenta nuevamente en unos instantes.")
                        .build()
        ));
        return summary;
    }

    private void normalizeSeverities(InsightSummary summary) {
        if (summary.getInsights() == null) return;
        summary.getInsights().forEach(insight -> {
            if (insight.getSeverity() == null) {
                insight.setSeverity(Insight.InsightSeverity.INFO);
                return;
            }
            try {
                insight.setSeverity(
                        Insight.InsightSeverity.valueOf(insight.getSeverity().name().toUpperCase())
                );
            } catch (IllegalArgumentException e) {
                insight.setSeverity(Insight.InsightSeverity.INFO);
            }
        });
    }
}