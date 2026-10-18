package com.elias.mismonedas.document.adapter.ocr;

import com.elias.mismonedas.document.port.ReceiptData;
import com.elias.mismonedas.document.port.ReceiptParserPort;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Qualifier("regex")
public class RegexReceiptParserAdapter implements ReceiptParserPort {

    private static final Map<Pattern, String> OCR_CLEANUP = new LinkedHashMap<>();
    static {
        OCR_CLEANUP.put(Pattern.compile("(?m)^cd\\s+"), "EFECTIVO ");
        OCR_CLEANUP.put(Pattern.compile("(?m)^:00$"), "VUELTO 00");
        OCR_CLEANUP.put(Pattern.compile("(?i)\\bENTA Y SEIS\\b"), "SON: SETENTA Y SEIS");
        OCR_CLEANUP.put(Pattern.compile("(?i)\\bENTA\\b"), "SON: SETENTA");
        OCR_CLEANUP.put(Pattern.compile("(?m)^JP,"), "OP.");
        OCR_CLEANUP.put(Pattern.compile("(?m)^SANT\\."), "CANT.");
        OCR_CLEANUP.put(Pattern.compile("(?m)^(?=\\d+%)"), "IGV ");
        OCR_CLEANUP.put(Pattern.compile("(?i)\\bGO\\b"), "PAGO");
        OCR_CLEANUP.put(Pattern.compile("(?i)\\bELTO\\b"), "VUELTO");
    }

    private static final Pattern TOTAL_PATTERN =
            Pattern.compile("^TOTAL\\s+([0-9]+[.,][0-9]{2})$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_SLASH_PATTERN =
            Pattern.compile("^TOTAL\\s+S/\\s*\\.?\\s*([0-9]+[.,][0-9]{2})$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final Pattern SON_PATTERN =
            Pattern.compile("SON:\\s*.*?([0-9]+[.,][0-9]{2})", Pattern.CASE_INSENSITIVE);

    private static final Pattern CASH_PATTERN =
            Pattern.compile("(?:EFECTIVO|cd|VUELTO)\\s*S?/?\\.?\\s*([0-9]+[.,][0-9]{2})", Pattern.CASE_INSENSITIVE);

    private static final Pattern FUZZY_TOTAL_PATTERN =
            Pattern.compile("^(?:O[TTA]{2,3}L|TOT[AI]L|TOTA|OTAL)\\s+([0-9]+[.,][0-9]{2})$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final Pattern BEFORE_SON_PATTERN =
            Pattern.compile("([0-9]+[.,][0-9]{2})\\s*\\nSON:", Pattern.CASE_INSENSITIVE);

    private static final Pattern ANY_AMOUNT_FALLBACK =
            Pattern.compile("([0-9]+[.,][0-9]{2})");

    private static final Map<String, Pattern> CURRENCY_PATTERNS = new LinkedHashMap<>();
    static {
        CURRENCY_PATTERNS.put("PEN", Pattern.compile("S/?\\.?\\s*|SOL(ES)?\\b", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("USD", Pattern.compile("US\\$|USD\\b|D[OÓ]LAR(ES)?\\b", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("EUR", Pattern.compile("[€]|EUR\\b", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("BRL", Pattern.compile("R\\$|REAL(ES)?\\b", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("MXN", Pattern.compile("MX\\$|MXN\\b", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("CLP", Pattern.compile("CLP\\b", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("COP", Pattern.compile("COP\\b", Pattern.CASE_INSENSITIVE));
    }

    private static final Pattern STANDALONE_AMOUNT_PATTERN =
            Pattern.compile("^([0-9]+[.,][0-9]{2})$", Pattern.MULTILINE);

    private static final Pattern TAX_LINE_PATTERN =
            Pattern.compile(".*(?:IGV|%|OP\\.|SUB\\s+TOTAL|GRAVADA|EXONERADA).*", Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_PATTERN =
            Pattern.compile("FECHA\\s*DE\\s*EMISION:\\s*(\\d{2})[-/](\\d{2})[-/](\\d{4})", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final Map<PaymentMethod, Pattern> PAYMENT_METHOD_PATTERNS = new LinkedHashMap<>();
    static {
        PAYMENT_METHOD_PATTERNS.put(PaymentMethod.CASH,
                Pattern.compile("(?:EFECTIVO|CONTADO|CD\\s*\\.?|PAGO\\s+EN\\s+EFECTIVO)", Pattern.CASE_INSENSITIVE));
        PAYMENT_METHOD_PATTERNS.put(PaymentMethod.CREDIT_CARD,
                Pattern.compile("(?:TARJETA\\s+CRED(?:ITO)?|TRJ?\\.?\\s*CRED(?:ITO)?|CREDITO|VISA|MASTERCARD|AMEX)", Pattern.CASE_INSENSITIVE));
        PAYMENT_METHOD_PATTERNS.put(PaymentMethod.DEBIT_CARD,
                Pattern.compile("(?:TARJETA\\s+DEB(?:ITO)?|TRJ?\\.?\\s*DEB(?:ITO)?|DEBITO)", Pattern.CASE_INSENSITIVE));
        PAYMENT_METHOD_PATTERNS.put(PaymentMethod.YAPE,
                Pattern.compile("\\bYAPE\\b", Pattern.CASE_INSENSITIVE));
        PAYMENT_METHOD_PATTERNS.put(PaymentMethod.PLIN,
                Pattern.compile("\\bPLIN\\b", Pattern.CASE_INSENSITIVE));
        PAYMENT_METHOD_PATTERNS.put(PaymentMethod.BANK_TRANSFER,
                Pattern.compile("(?:TRANSFERENCIA|TRANSF\\.|BANCO|DEPOSITO|CTA\\s+CTE)", Pattern.CASE_INSENSITIVE));
    }

    private static final Map<TransactionType, Pattern> TRANSACTION_TYPE_PATTERNS = new LinkedHashMap<>();
    static {
        TRANSACTION_TYPE_PATTERNS.put(TransactionType.SPENT,
                Pattern.compile("(?:BOLETA|FACTURA|VENTA|COMPRA|SERVICIO|PEDIDO|CUENTA|TIKTET)", Pattern.CASE_INSENSITIVE));
        TRANSACTION_TYPE_PATTERNS.put(TransactionType.INCOME,
                Pattern.compile("(?:COBRO|INGRESO|RECAUDACION|PAGO\\s+RECIBIDO|INGRESOS)", Pattern.CASE_INSENSITIVE));
    }

    @Override
    public ReceiptData parse(String rawOcrText) {
        String cleaned = normalize(rawOcrText);
        BigDecimal amount = extractAmount(cleaned);
        boolean flexible = false;
        if (amount == null) {
            amount = extractFlexibleAmount(cleaned);
            flexible = amount != null;
        }
        String currency = extractCurrencyCode(cleaned);
        LocalDate date = extractDate(cleaned);
        String description = extractDescription(cleaned);
        PaymentMethod paymentMethod = extractPaymentMethod(cleaned);
        TransactionType transactionType = extractTransactionType(cleaned);

        double confidence = 0.0;
        if (amount != null) {
            confidence = flexible ? 0.3 : 0.5;
            if (date != null) confidence = flexible ? 0.5 : 0.7;
        }

        return new ReceiptData(amount, date, description, currency, paymentMethod, transactionType, confidence, null);
    }

    private String normalize(String text) {
        String result = text;
        for (Map.Entry<Pattern, String> entry : OCR_CLEANUP.entrySet()) {
            result = entry.getKey().matcher(result).replaceAll(entry.getValue());
        }
        return result;
    }

    private BigDecimal extractAmount(String text) {
        Matcher m = TOTAL_PATTERN.matcher(text);
        if (m.find()) return parseAmount(m.group(1));
        m = AMOUNT_SLASH_PATTERN.matcher(text);
        if (m.find()) return parseAmount(m.group(1));
        m = SON_PATTERN.matcher(text);
        if (m.find()) return parseAmount(m.group(1));
        m = CASH_PATTERN.matcher(text);
        if (m.find()) return parseAmount(m.group(1));
        m = FUZZY_TOTAL_PATTERN.matcher(text);
        if (m.find()) return parseAmount(m.group(1));
        m = BEFORE_SON_PATTERN.matcher(text);
        if (m.find()) return parseAmount(m.group(1));
        BigDecimal standalone = extractStandaloneAmount(text);
        if (standalone != null) return standalone;
        return null;
    }

    private BigDecimal extractFlexibleAmount(String text) {
        String[] lines = text.split("\\r?\\n");
        java.util.TreeSet<BigDecimal> candidates = new java.util.TreeSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            String[] parts = trimmed.split("\\s+");
            for (String part : parts) {
                if (part.matches("^[0-9]+[.,][0-9]{2}$")) {
                    try {
                        candidates.add(new BigDecimal(part.replace(",", ".")));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.last();
    }

    private static BigDecimal parseAmount(String raw) {
        return new BigDecimal(raw.replace(",", "."));
    }

    private BigDecimal extractStandaloneAmount(String text) {
        String[] lines = text.split("\\r?\\n");
        boolean afterTaxLine = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (afterTaxLine && trimmed.matches("^[0-9]+[.,][0-9]{2}$")) {
                return new BigDecimal(trimmed.replace(",", "."));
            }
            afterTaxLine = TAX_LINE_PATTERN.matcher(trimmed).matches();
        }
        return null;
    }

    private LocalDate extractDate(String text) {
        Matcher m = DATE_PATTERN.matcher(text);
        if (m.find()) {
            String day = m.group(1);
            String month = m.group(2);
            String year = m.group(3);
            return LocalDate.parse(day + "-" + month + "-" + year, DATE_FORMATTER);
        }
        return null;
    }

    private String extractDescription(String text) {
        String[] lines = text.split("\\r?\\n");
        List<String> candidates = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()
                    && !trimmed.matches("(?i)^(TOTAL|SUBTOTAL|IGV|OP\\.|SON:|EFECTIVO|VUELTO|DOC\\.|FORMA|CUENTE|MOZO|CANT\\.|SUB\\s+TOTAL|BOLETA|FACTURA|FECHA|TIPO|DIRECCION).*")
                    && !trimmed.matches(".*\\d{2}-\\d{2}-\\d{4}.*")
                    && !trimmed.matches(".*[0-9]+[.,][0-9]{2}.*")) {
                candidates.add(trimmed);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.getLast();
    }

    private String extractCurrencyCode(String text) {
        for (var entry : CURRENCY_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(text).find()) {
                return entry.getKey();
            }
        }
        return "PEN";
    }

    private PaymentMethod extractPaymentMethod(String text) {
        for (var entry : PAYMENT_METHOD_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(text).find()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private TransactionType extractTransactionType(String text) {
        for (var entry : TRANSACTION_TYPE_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(text).find()) {
                return entry.getKey();
            }
        }
        return null;
    }
}
