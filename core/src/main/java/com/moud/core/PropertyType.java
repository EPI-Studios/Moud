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
    };

    public abstract ValidationResult validate(String value);
}
