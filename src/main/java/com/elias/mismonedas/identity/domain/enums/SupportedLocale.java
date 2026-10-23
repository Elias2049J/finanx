package com.elias.mismonedas.identity.domain.enums;

import java.util.Arrays;

public enum SupportedLocale {
    ES_PE("es-PE"),
    EN_US("en-US");

    public static final String DEFAULT_TAG = "es-PE";

    private final String tag;

    SupportedLocale(String tag) {
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }

    public static String canonicalize(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_TAG;
        }

        String candidate = value.trim();
        return Arrays.stream(values())
                .filter(locale -> locale.tag.equalsIgnoreCase(candidate))
                .map(SupportedLocale::getTag)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported locale: " + value));
    }

    public static String canonicalizeOrDefault(String value) {
        try {
            return canonicalize(value);
        } catch (IllegalArgumentException ex) {
            return DEFAULT_TAG;
        }
    }
}
