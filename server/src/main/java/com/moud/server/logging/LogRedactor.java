package com.moud.server.logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility for stripping sensitive data from log messages and structured context.
 */
public final class LogRedactor {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?i)(token|session|auth|key)=([a-z0-9\\-_.]+)");
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:(?:2[0-4]\\d|25[0-5]|[01]?\\d\\d?)\\.){3}(?:2[0-4]\\d|25[0-5]|[01]?\\d\\d?)\\b");

    private LogRedactor() {
    }

    public static String redact(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String sanitized = EMAIL_PATTERN.matcher(message).replaceAll("[REDACTED_EMAIL]");
        sanitized = UUID_PATTERN.matcher(sanitized).replaceAll("[REDACTED_UUID]");
        sanitized = TOKEN_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED_TOKEN]");
        sanitized = IP_PATTERN.matcher(sanitized).replaceAll("[REDACTED_IP]");
        return sanitized;
    }

    public static Map<String, Object> redactContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> sanitized = new HashMap<>(context.size());
        context.forEach((key, value) -> {
            if (key != null && !key.isEmpty() && value != null) {
                sanitized.put(key, redactValue(value));
            }
        });
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private static Object redactValue(Object value) {
        if (value instanceof String) {
            return redact((String) value);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new HashMap<>();
            map.forEach((key, nestedValue) -> {
                if (key instanceof String strKey && nestedValue != null) {
                    nested.put(strKey, redactValue(nestedValue));
                }
            });
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> sanitizedList = new ArrayList<>(list.size());
            for (Object element : list) {
                sanitizedList.add(redactValue(element));
            }
            return sanitizedList;
        }
        if (value instanceof Object[] array) {
            Object[] sanitizedArray = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
                sanitizedArray[i] = redactValue(array[i]);
            }
            return sanitizedArray;
        }
        return value;
    }
}

