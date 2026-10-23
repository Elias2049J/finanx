package com.elias.mismonedas.shared.i18n;

import com.elias.mismonedas.identity.domain.enums.SupportedLocale;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JsonMessageCatalog {

    private static final String CATALOG_PATTERN = "classpath*:i18n/messages_*.json";
    private static final String SPANISH_LOCALE = SupportedLocale.DEFAULT_TAG;

    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    private volatile Map<String, Map<String, String>> catalogs = Map.of();

    @PostConstruct
    public void initialize() {
        Map<String, Map<String, String>> loaded = new HashMap<>();
        try {
            for (Resource resource : resourceResolver.getResources(CATALOG_PATTERN)) {
                String filename = resource.getFilename();
                if (filename == null || !filename.startsWith("messages_") || !filename.endsWith(".json")) {
                    continue;
                }

                String localeTag = filename.substring("messages_".length(), filename.length() - ".json".length());
                String canonicalLocale = SupportedLocale.canonicalize(localeTag);
                Map<String, String> messages;
                try (InputStream input = resource.getInputStream()) {
                    messages = objectMapper.readValue(input, new TypeReference<>() { });
                }
                loaded.put(canonicalLocale, Map.copyOf(messages));
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("Could not load JSON message catalogs", ex);
        }

        if (!loaded.containsKey(SPANISH_LOCALE)) {
            throw new IllegalStateException("Required Spanish fallback catalog is missing: " + SPANISH_LOCALE);
        }
        catalogs = Map.copyOf(loaded);
    }

    public String getMessage(String key, Locale locale, Object... arguments) {
        String localeTag = SupportedLocale.canonicalizeOrDefault(locale == null ? null : locale.toLanguageTag());
        Map<String, String> localizedCatalog = catalogs.getOrDefault(localeTag, Map.of());
        String pattern = localizedCatalog.get(key);
        if (pattern == null) {
            pattern = catalogs.get(SPANISH_LOCALE).get(key);
        }
        if (pattern == null) {
            return key;
        }
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, SupportedLocale.toLocale(localeTag)).format(arguments);
    }
}
