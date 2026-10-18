package com.elias.mismonedas.shared.repository;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
                    .map(property -> cb.like(cb.lower(resolve(root, property)), pattern, ESCAPE))
                    .toArray(Predicate[]::new);
            return cb.or(matches);
        };
    }

    private static <T> Expression<String> resolve(Root<T> root, String property) {
        String[] segments = property.split("\\.");
        Path<?> path = root;
        for (int i = 0; i < segments.length - 1; i++) {
            path = ((From<?, ?>) path).join(segments[i], JoinType.LEFT);
        }
        return path.get(segments[segments.length - 1]).as(String.class);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
