package com.elias.mismonedas.document.port;

import com.elias.mismonedas.document.port.ReceiptData;

public interface ReceiptParserPort {
    ReceiptData parse(String rawOcrText);
}
