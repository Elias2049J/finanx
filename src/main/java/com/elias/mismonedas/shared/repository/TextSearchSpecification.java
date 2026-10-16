package com.elias.mismonedas.shared.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Locale;

public final class TextSearchSpecification {

    private static final char ESCAPE = '\\';

    private TextSearchSpecification() {
    }

    public static <T> Specification<T> containsIgnoreCase(String term, String... properties) {
        if (term == null || term.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }

        String pattern = "%" + escape(term.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            Predicate[] matches = Arrays.stream(properties)
                    .map(property -> cb.like(cb.lower(root.get(property)), pattern, ESCAPE))
                    .toArray(Predicate[]::new);
            return cb.or(matches);
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
