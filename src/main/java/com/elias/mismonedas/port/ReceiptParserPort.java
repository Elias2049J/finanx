package com.elias.mismonedas.port;

public interface ReceiptParserPort {
    ReceiptData parse(String rawOcrText);
}
