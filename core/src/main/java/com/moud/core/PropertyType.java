package com.moud.core;


public enum PropertyType {
    STRING {
        @Override
        public ValidationResult validate(String value) {
            return ValidationResult.success();
        }
    },
    INT {
        @Override
        public ValidationResult validate(String value) {
            try {
                Integer.parseInt(value == null ? "" : value.trim());
                return ValidationResult.success();
            } catch (Exception e) {
                return ValidationResult.failure("expected int");
            }
        }
    },
    FLOAT {
        @Override
        public ValidationResult validate(String value) {
            try {
                Float.parseFloat(value == null ? "" : value.trim());
                return ValidationResult.success();
            } catch (Exception e) {
                return ValidationResult.failure("expected float");
            }
        }
    },
    BOOL {
        @Override
        public ValidationResult validate(String value) {
            String v = value == null ? "" : value.trim().toLowerCase();
            if ("true".equals(v) || "false".equals(v) || "1".equals(v) || "0".equals(v)) {
                return ValidationResult.success();
            }
            return ValidationResult.failure("expected bool");
        }
    },
    CURVE {
        @Override
        public ValidationResult validate(String value) {
            if (value == null || value.isBlank()) return ValidationResult.success();
            for (String seg : value.split("\\|")) {
                String s = seg.trim();
                if (s.isEmpty()) continue;
                int colon = s.indexOf(':');
                if (colon <= 0 || colon == s.length() - 1) {
                    return ValidationResult.failure("expected t:v|t:v...");
                }
                try {
                    Float.parseFloat(s.substring(0, colon).trim());
                    Float.parseFloat(s.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    return ValidationResult.failure("expected numeric t:v pairs");
                }
            }
            return ValidationResult.success();
        }
    };

    public abstract ValidationResult validate(String value);
}
