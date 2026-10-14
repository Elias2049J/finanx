package com.elias.mismonedas.document.port;

public record OcrResult(String rawText, double engineConfidence, long durationMs) {
}
