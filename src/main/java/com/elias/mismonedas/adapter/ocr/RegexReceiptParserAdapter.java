package com.elias.mismonedas.adapter.ocr;

import com.elias.mismonedas.port.ReceiptData;
import com.elias.mismonedas.port.ReceiptParserPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Qualifier("regex")
public class RegexReceiptParserAdapter implements ReceiptParserPort {

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?:S/\\.?|TOTAL)\\s*([0-9]+[.,][0-9]{2})", Pattern.CASE_INSENSITIVE);

    @Override
    public ReceiptData parse(String rawOcrText) {
        Matcher m = AMOUNT_PATTERN.matcher(rawOcrText);
        BigDecimal amount = m.find() ? new BigDecimal(m.group(1).replace(",", ".")) : null;
        double confidence = amount != null ? 0.5 : 0.0;
        return new ReceiptData(amount, null, null, "PEN", confidence);
    }
}
