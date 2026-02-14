package com.moud.core;

public record ValidationResult(boolean ok, String message) {
    public static ValidationResult success() {
        return new ValidationResult(true, "");
    }

    public static ValidationResult failure(String message) {
        return new ValidationResult(false, message == null ? "" : message);
    }
}

