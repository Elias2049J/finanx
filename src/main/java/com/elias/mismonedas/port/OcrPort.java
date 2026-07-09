package com.elias.mismonedas.port;

public interface OcrPort {
    OcrResult extractText(byte[] imageBytes);
}
