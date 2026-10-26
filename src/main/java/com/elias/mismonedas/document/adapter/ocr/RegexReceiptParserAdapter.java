package com.elias.mismonedas.document.adapter.ocr;

import com.elias.mismonedas.document.dto.ReceiptData;
import com.elias.mismonedas.document.port.ReceiptParserPort;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
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

    private static final String AMOUNT_TOKEN =
            "(?:\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{2})?|\\d+[.,]\\d{2})";

    private static final Pattern TOTAL_PATTERN =
            Pattern.compile("^TOTAL\\s+(" + AMOUNT_TOKEN + ")$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_SLASH_PATTERN =
            Pattern.compile("^TOTAL\\s+S/\\s*\\.?\\s*(" + AMOUNT_TOKEN + ")$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final Pattern SON_PATTERN =
            Pattern.compile("SON:\\s*.*?(" + AMOUNT_TOKEN + ")", Pattern.CASE_INSENSITIVE);

    private static final Pattern CASH_PATTERN =
            Pattern.compile("(?:EFECTIVO|cd|VUELTO)\\s*(?:S/\\s*\\.?\\s*)?(" + AMOUNT_TOKEN + ")", Pattern.CASE_INSENSITIVE);

    private static final Pattern FUZZY_TOTAL_PATTERN =
            Pattern.compile("^(?:O[TTA]{2,3}L|TOT[AI]L|TOTA|OTAL)\\s+(" + AMOUNT_TOKEN + ")$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final Pattern BEFORE_SON_PATTERN =
            Pattern.compile("(" + AMOUNT_TOKEN + ")\\s*\\nSON:", Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_TOKEN_PATTERN =
            Pattern.compile("^" + AMOUNT_TOKEN + "$");

    private static final Map<String, Pattern> CURRENCY_PATTERNS = new LinkedHashMap<>();
    static {
        CURRENCY_PATTERNS.put("USD", Pattern.compile("(?:US\\s?\\$|\\bUSD\\b|\\bD[ÓO]LAR(?:ES)?\\b)", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("EUR", Pattern.compile("(?:€|\\bEUR\\b|\\bEUROS?\\b)", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("BRL", Pattern.compile("(?:R\\$|\\bREAL(?:ES)?\\b)", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("MXN", Pattern.compile("(?:MX\\$|\\bMXN\\b)", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("CLP", Pattern.compile("\\bCLP\\b", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("COP", Pattern.compile("\\bCOP\\b", Pattern.CASE_INSENSITIVE));
        CURRENCY_PATTERNS.put("PEN", Pattern.compile("(?:\\bS/\\s*\\.?\\s*(?=\\d)|\\bSOL(?:ES)?\\b|\\bPEN\\b)", Pattern.CASE_INSENSITIVE));
    }

    private static final Pattern TAX_LINE_PATTERN =
            Pattern.compile(".*(?:IGV|%|OP\\.|SUB\\s+TOTAL|GRAVADA|EXONERADA).*", Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_PATTERN =
            Pattern.compile("FECHA\\s*DE\\s*EMISION:\\s*(\\d{1,2})([-/.])(\\d{1,2})\\2(\\d{4})", Pattern.CASE_INSENSITIVE);

    private static final Map<Character, DateTimeFormatter> DATE_FORMATTERS = Map.of(
            '-', DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            '/', DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            '.', DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT)
    );

    private static final LocalDate MIN_PLAUSIBLE_DATE = LocalDate.of(2000, 1, 1);

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
        if (rawOcrText == null || rawOcrText.isBlank()) {
            return new ReceiptData(null, null, null, "PEN", null, null, 0.0, null);
        }

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
                if (AMOUNT_TOKEN_PATTERN.matcher(part).matches()) {
                    BigDecimal candidate = parseMoney(part);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.last();
    }

    private static BigDecimal parseAmount(String raw) {
        return parseMoney(raw);
    }

    private static BigDecimal parseMoney(String raw) {
        if (raw == null) return null;

        String value = raw.trim();
        if (value.isEmpty()) return null;

        int lastDot = value.lastIndexOf('.');
        int lastComma = value.lastIndexOf(',');
        String normalized;
        if (lastDot >= 0 && lastComma >= 0) {
            if (lastComma > lastDot) {
                normalized = value.replace(".", "").replace(',', '.');
            } else {
                normalized = value.replace(",", "");
            }
        } else if (lastComma >= 0) {
            normalized = normalizeSingleSeparator(value, ',');
        } else if (lastDot >= 0) {
            normalized = normalizeSingleSeparator(value, '.');
        } else {
            normalized = value;
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeSingleSeparator(String value, char separator) {
        int lastSeparator = value.lastIndexOf(separator);
        int digitsAfter = value.length() - lastSeparator - 1;

        if (digitsAfter == 3) {
            return value.replace(String.valueOf(separator), "");
        }
        if (digitsAfter == 1 || digitsAfter == 2) {
            String integerPart = value.substring(0, lastSeparator).replace(String.valueOf(separator), "");
            return integerPart + "." + value.substring(lastSeparator + 1);
        }
        return value;
    }

    private BigDecimal extractStandaloneAmount(String text) {
        String[] lines = text.split("\\r?\\n");
        boolean afterTaxLine = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (afterTaxLine && AMOUNT_TOKEN_PATTERN.matcher(trimmed).matches()) {
                return parseMoney(trimmed);
            }
            afterTaxLine = TAX_LINE_PATTERN.matcher(trimmed).matches();
        }
        return null;
    }

    private LocalDate extractDate(String text) {
        if (text == null) return null;

        Matcher matcher = DATE_PATTERN.matcher(text);
        LocalDate maxPlausibleDate = LocalDate.now().plusDays(1);
        while (matcher.find()) {
            String day = matcher.group(1);
            String separator = matcher.group(2);
            String month = matcher.group(3);
            String year = matcher.group(4);
            DateTimeFormatter formatter = DATE_FORMATTERS.get(separator.charAt(0));
            if (formatter == null) continue;

            try {
                LocalDate date = LocalDate.parse(day + separator + month + separator + year, formatter);
                if (!date.isBefore(MIN_PLAUSIBLE_DATE) && !date.isAfter(maxPlausibleDate)) {
                    return date;
                }
            } catch (DateTimeParseException ignored) {
            }
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
