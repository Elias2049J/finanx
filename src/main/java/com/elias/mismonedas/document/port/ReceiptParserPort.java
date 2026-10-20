package com.elias.mismonedas.document.port;

import com.elias.mismonedas.document.dto.ReceiptData;

public interface ReceiptParserPort {
    ReceiptData parse(String rawOcrText);
}
