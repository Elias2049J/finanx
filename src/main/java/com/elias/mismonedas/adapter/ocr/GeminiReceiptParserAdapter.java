package com.elias.mismonedas.adapter.ocr;

import com.elias.mismonedas.adapter.ai.gemini.InteractionClient;
import com.elias.mismonedas.adapter.ai.gemini.dto.InteractionRequest;
import com.elias.mismonedas.adapter.ai.gemini.dto.InteractionResponse;
import com.elias.mismonedas.port.ReceiptData;
import com.elias.mismonedas.port.ReceiptParserPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Component
@Qualifier("gemini")
@RequiredArgsConstructor
public class GeminiReceiptParserAdapter implements ReceiptParserPort {

    private static final String SYSTEM_EXTRACT_PROMPT =
            "Extract the total amount, date (ISO format), and merchant name from the receipt text below. " +
            "Respond with valid JSON only: {\"amount\": ..., \"date\": \"...\", \"merchant\": \"...\"}. " +
            "Use null for missing fields. No explanation, no markdown.";

    private final InteractionClient geminiClient;
    private final ObjectMapper objectMapper;

    @Value("${ocr.gemini.model:gemini-2.5-flash}")
    private String model;

    @Override
    public ReceiptData parse(String rawOcrText) {
        String prompt = buildStructuredExtractionPrompt(rawOcrText);

        InteractionRequest request = InteractionRequest.builder()
                .model(model)
                .input(prompt)
                .systemInstruction(SYSTEM_EXTRACT_PROMPT)
                .generationConfig(new InteractionRequest.GenerationConfig(0.1f, 300))
                .store(false)
                .build();

        InteractionResponse response = geminiClient.create(request);
        String jsonResponse = response.extractOutputText();

        return parseJsonToReceiptData(jsonResponse);
    }

    private String buildStructuredExtractionPrompt(String rawOcrText) {
        return """
                Extrae del siguiente texto de un recibo/boleta peruano: monto total,
                fecha (formato ISO), y nombre del comercio. Responde SOLO JSON válido
                con las claves amount, date, merchant. Si no encuentras un dato, usa null.

                Texto:
                %s
                """.formatted(rawOcrText);
    }

    @SuppressWarnings("unchecked")
    private ReceiptData parseJsonToReceiptData(String json) {
        if (json == null || json.isBlank()) {
            return new ReceiptData(null, null, null, "PEN", 0.0);
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            BigDecimal amount = map.get("amount") != null
                    ? new BigDecimal(map.get("amount").toString())
                    : null;
            LocalDate date = map.get("date") != null
                    ? LocalDate.parse(map.get("date").toString())
                    : null;
            String merchant = map.get("merchant") != null
                    ? map.get("merchant").toString()
                    : null;
            return new ReceiptData(amount, date, merchant, "PEN", 0.85);
        } catch (JsonProcessingException e) {
            return new ReceiptData(null, null, null, "PEN", 0.0);
        }
    }
}
