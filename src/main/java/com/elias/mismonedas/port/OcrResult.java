package com.elias.mismonedas.port;

public record OcrResult(String rawText, double engineConfidence, long durationMs) {
}
