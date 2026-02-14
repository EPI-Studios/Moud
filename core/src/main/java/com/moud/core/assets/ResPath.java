package com.moud.core.assets;

import com.moud.core.ValidationResult;

import java.util.Objects;

public record ResPath(String value) {
    public static final String SCHEME = "res://";

    public ResPath {
        Objects.requireNonNull(value, "value");
        String normalized = normalize(value);
        ValidationResult validation = validate(normalized);
        if (!validation.ok()) {
            throw new IllegalArgumentException("Invalid res path: " + validation.message());
        }
        value = normalized;
    }

    public static ResPath of(String value) {
        return new ResPath(value);
    }

    public static ValidationResult validate(String value) {
        if (value == null) {
            return ValidationResult.failure("value is null");
        }
        if (!value.startsWith(SCHEME)) {
            return ValidationResult.failure("must start with " + SCHEME);
        }
        String rest = value.substring(SCHEME.length());
        if (rest.isBlank()) {
            return ValidationResult.failure("path is empty");
        }
        if (rest.startsWith("/")) {
            return ValidationResult.failure("must not start with '/' after scheme");
        }
        if (value.indexOf('\\') >= 0) {
            return ValidationResult.failure("backslashes are not allowed");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return ValidationResult.failure("control characters are not allowed");
            }
            if (c == ':' && i >= SCHEME.length()) {
                return ValidationResult.failure("':' is not allowed in path");
            }
            if (c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
                return ValidationResult.failure("invalid character in path: '" + c + "'");
            }
        }

        String[] parts = rest.split("/");
        for (String part : parts) {
            if (part.isEmpty()) {
                return ValidationResult.failure("empty path segment");
            }
            if (".".equals(part) || "..".equals(part)) {
                return ValidationResult.failure("relative segments are not allowed");
            }
        }
        return ValidationResult.success();
    }

    public String path() {
        return value.substring(SCHEME.length());
    }

    private static String normalize(String raw) {
        String value = raw.trim();
        if (!value.startsWith(SCHEME)) {
            return value;
        }
        String rest = value.substring(SCHEME.length());
        while (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        rest = rest.replaceAll("/{2,}", "/");
        while (rest.endsWith("/") && rest.length() > 1) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return SCHEME + rest;
    }
}

