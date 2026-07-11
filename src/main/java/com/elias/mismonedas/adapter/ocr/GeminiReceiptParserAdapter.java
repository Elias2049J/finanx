package com.elias.mismonedas.adapter.ocr;

import com.elias.mismonedas.adapter.ai.gemini.InteractionClient;
import com.elias.mismonedas.adapter.ai.gemini.dto.InteractionRequest;
import com.elias.mismonedas.adapter.ai.gemini.dto.InteractionResponse;
import com.elias.mismonedas.entity.enums.PaymentMethod;
import com.elias.mismonedas.entity.enums.TransactionType;
import com.elias.mismonedas.exception.OcrProcessingException;
import com.elias.mismonedas.port.ReceiptData;
import com.elias.mismonedas.port.ReceiptParserPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            "Eres un extractor de datos de recibos/boletas peruanos. " +
            "Extrae los siguientes campos del texto OCR y responde SOLO con JSON válido (sin markdown, sin explicación):\n" +
            "{\n" +
            "  \"amount\": número o null,\n" +
            "  \"date\": \"YYYY-MM-DD\" o null (si la fecha no es visible, devuelve null sin error),\n" +
            "  \"description\": string con el nombre/descripción del comercio o null,\n" +
            "  \"currency\": \"PEN\" por defecto,\n" +
            "  \"paymentMethod\": \"CASH\" | \"CREDIT_CARD\" | \"DEBIT_CARD\" | \"BANK_TRANSFER\" | \"YAPE\" | \"PLIN\" | \"OTHER\" o null,\n" +
            "  \"transactionType\": \"SPENT\" | \"INCOME\" o null\n" +
            "}\n" +
            "Reglas:\n" +
            "- Si no encuentras un campo, usa null.\n" +
            "- paymentMethod: detecta keywords como EFECTIVO/CASH/CD (CASH), TARJETA VISA/MASTERCARD/TC (CREDIT_CARD), TARJETA DEBITO/TD (DEBIT_CARD), YAPE, PLIN, TRANSFERENCIA/DEPOSITO (BANK_TRANSFER).\n" +
            "- transactionType: BOLETA/FACTURA/VENTA/COMPRA = SPENT, COBRO/INGRESO/RECAUDACION = INCOME. Si no se detecta, null.";

    private static final Logger log = LoggerFactory.getLogger(GeminiReceiptParserAdapter.class);

    private final InteractionClient geminiClient;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.model.primary}")
    private String model;

    @Override
    public ReceiptData parse(String rawOcrText) {
        String prompt = buildStructuredExtractionPrompt(rawOcrText);

        InteractionRequest request = InteractionRequest.builder()
                .model(model)
                .input(prompt)
                .systemInstruction(SYSTEM_EXTRACT_PROMPT)
                .generationConfig(new InteractionRequest.GenerationConfig(0.1f, 2048))
                .store(false)
                .build();

        InteractionResponse response = geminiClient.create(request);
        String jsonResponse = response.extractOutputText();

        log.info("gemini_raw_json_response={}", jsonResponse);

        return parseJsonToReceiptData(jsonResponse);
    }

    private String buildStructuredExtractionPrompt(String rawOcrText) {
        return """
                Extrae del siguiente texto de un recibo/boleta peruano: monto total,
                fecha (formato ISO), descripción del comercio, método de pago y tipo de transacción.
                Responde SOLO JSON válido con las claves amount, date, description, currency,
                paymentMethod y transactionType. Si no encuentras un dato, usa null.

                Texto:
                %s
                """.formatted(rawOcrText);
    }

    @SuppressWarnings("unchecked")
    private ReceiptData parseJsonToReceiptData(String json) {
        if (json == null || json.isBlank()) {
            log.warn("gemini_response_empty_or_null");
            throw new OcrProcessingException("Gemini no devolvió una respuesta JSON válida");
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            BigDecimal amount = map.get("amount") != null
                    ? new BigDecimal(map.get("amount").toString())
                    : null;
            LocalDate date = map.get("date") != null && !map.get("date").toString().isBlank()
                    ? LocalDate.parse(map.get("date").toString())
                    : null;
            String description = map.get("description") != null
                    ? map.get("description").toString()
                    : null;
            if (description == null && map.get("merchant") != null) {
                description = map.get("merchant").toString();
            }
            String currency = map.get("currency") != null
                    ? map.get("currency").toString()
                    : "PEN";
            PaymentMethod paymentMethod = parsePaymentMethod(map.get("paymentMethod"));
            TransactionType transactionType = parseTransactionType(map.get("transactionType"));

            if (amount == null && description == null) {
                log.warn("gemini_parsed_null_fields amount=null description=null", description);
                throw new OcrProcessingException("Gemini no pudo extraer datos del recibo");
            }
            return new ReceiptData(amount, date, description, currency, paymentMethod, transactionType, 0.85);
        } catch (JsonProcessingException e) {
            log.warn("gemini_json_parse_error json={}", json, e);
            throw new OcrProcessingException("Error al interpretar la respuesta de Gemini: " + e.getMessage());
        }
    }

    private PaymentMethod parsePaymentMethod(Object value) {
        if (value == null) return null;
        try {
            return PaymentMethod.valueOf(value.toString().toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("gemini_unknown_paymentMethod value={}", value);
            return null;
        }
    }

    private TransactionType parseTransactionType(Object value) {
        if (value == null) return null;
        try {
            return TransactionType.valueOf(value.toString().toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("gemini_unknown_transactionType value={}", value);
            return null;
        }
    }
}
