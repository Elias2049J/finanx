package com.elias.mismonedas.document.port;

public interface OcrPort {
    OcrResult extractText(byte[] imageBytes);
}
