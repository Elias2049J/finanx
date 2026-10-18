package com.elias.mismonedas.document.adapter.ocr;

import com.elias.mismonedas.document.port.ReceiptData;
import com.elias.mismonedas.document.port.ReceiptParserPort;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.shared.adapter.ai.gemini.InteractionClient;
import com.elias.mismonedas.shared.adapter.ai.gemini.dto.InteractionRequest;
import com.elias.mismonedas.shared.adapter.ai.gemini.dto.InteractionResponse;
import com.elias.mismonedas.shared.exception.OcrProcessingException;
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
            "Tu trabajo es EXTRAER TODOS LOS CAMPOS posibles del texto OCR. " +
            "Responde SOLO con JSON válido (sin markdown, sin explicación):\n" +
            "{\n" +
            "  \"amount\": número decimal con el MONTO TOTAL del recibo (obligatorio si hay cualquier número),\n" +
            "  \"date\": \"YYYY-MM-DD\" (busca fechas en cualquier formato: DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY, etc),\n" +
            "  \"description\": string con el nombre/descripción del comercio (la línea que identifica al negocio),\n" +
            "  \"currency\": \"PEN\" por defecto (soles), \"USD\" si detectas dólares,\n" +
            "  \"paymentMethod\": uno de estos valores exactos:\n" +
            "    - \"CASH\" si ves EFECTIVO, CONTADO, CD, PAGO EN EFECTIVO\n" +
            "    - \"CREDIT_CARD\" si ves TARJETA CREDITO, VISA, MASTERCARD, AMEX, TC, TRJ CRED\n" +
            "    - \"DEBIT_CARD\" si ves TARJETA DEBITO, TD, TRJ DEB\n" +
            "    - \"BANK_TRANSFER\" si ves TRANSFERENCIA, DEPOSITO, BANCO, CTA CTE\n" +
            "    - \"YAPE\" si ves YAPE\n" +
            "    - \"PLIN\" si ves PLIN\n" +
            "    - \"OTHER\" si no coincide con ninguno\n" +
            "  \"transactionType\": \"SPENT\" si es compra/venta/gasto (BOLETA, FACTURA, VENTA, COMPRA, SERVICIO), \"INCOME\" si es cobro/ingreso,\n" +
            "  \"category\": string con la categoría del gasto basándote en TODO el texto (ej: Alimentación, Transporte, Servicios, Entretenimiento, Salud, Educación, Ropa, Hogar, etc.)\n" +
            "}\n" +
            "REGLAS IMPORTANTES:\n" +
            "- EXTRAÉ TODOS los campos posibles. Solo usa null si realmente no hay información en el texto.\n" +
            "- El amount es el MONTO TOTAL (la línea que dice TOTAL o el monto más grande).\n" +
            "- La description es el nombre del comercio/negocio (generalmente al inicio del recibo).\n" +
            "- Analiza TODO el texto para detectar paymentMethod, transactionType y category.\n" +
            "- Si ves un monto con S/. o S/ es PEN (soles peruanos).\n" +
            "- Para category: infiere la categoría del gasto basándote en el tipo de negocio y productos/servicios mencionados.";

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

        return parseJsonToReceiptData(stripMarkdownFences(jsonResponse));
    }

    private String buildStructuredExtractionPrompt(String rawOcrText) {
        return """
                Analiza este recibo/boleta peruano y extrae TODOS los campos disponibles:
                - amount: monto total (busca líneas con TOTAL, SUBTOTAL, o el monto más grande)
                - date: fecha de emisión (cualquier formato: DD/MM/YYYY, DD-MM-YYYY, etc.)
                - description: nombre del comercio/negocio (generalmente al inicio)
                - currency: moneda (PEN si ves S/ o SOLES, USD si ves dólares)
                - paymentMethod: cómo se pagó (efectivo, tarjeta, yape, plin, etc.)
                - transactionType: si es gasto (SPENT) o ingreso (INCOME)
                - category: categoría del gasto basándote en el tipo de negocio (Alimentación, Transporte, Servicios, etc.)

                Responde SOLO JSON válido con estas 7 claves. Solo usa null si no hay información.

                Texto OCR:
                %s
                """.formatted(rawOcrText);
    }

    private String stripMarkdownFences(String text) {
        if (text == null) return null;
        String trimmed = text.strip();
        trimmed = trimmed.replaceAll("(?s)^```(?:json)?\\s*\n?", "");
        trimmed = trimmed.replaceAll("(?s)\n?```$","");
        return trimmed.strip();
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
            String category = map.get("category") != null
                    ? map.get("category").toString()
                    : null;

            if (amount == null && description == null) {
                log.warn("gemini_parsed_null_fields amount=null description=null", description);
                throw new OcrProcessingException("Gemini no pudo extraer datos del recibo");
            }
            log.info("gemini_parsed_fields paymentMethod={} transactionType={} category={}", paymentMethod, transactionType, category);
            return new ReceiptData(amount, date, description, currency, paymentMethod, transactionType, 0.85, category);
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
