package com.moud.server.api.validation;

import com.moud.server.api.exception.APIException;
import org.graalvm.polyglot.Value;

import java.util.Set;
import java.util.regex.Pattern;

public class APIValidator {
    private static final Pattern EVENT_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final int MAX_EVENT_NAME_LENGTH = 64;
    private static final int MAX_STRING_LENGTH = 65535;

    private static final Set<String> RESERVED_EVENT_NAMES = Set.of(
            "system.shutdown", "system.init", "internal.error"
    );

    public void validateEventName(String eventName) {
        if (eventName == null) {
            throw new APIException("INVALID_EVENT_NAME", "Event name cannot be null");
        }

        if (eventName.trim().isEmpty()) {
            throw new APIException("INVALID_EVENT_NAME", "Event name cannot be empty");
        }

        if (eventName.length() > MAX_EVENT_NAME_LENGTH) {
            throw new APIException("INVALID_EVENT_NAME", "Event name too long: max " + MAX_EVENT_NAME_LENGTH + " characters");
        }

        if (!EVENT_NAME_PATTERN.matcher(eventName).matches()) {
            throw new APIException("INVALID_EVENT_NAME", "Event name contains invalid characters: " + eventName);
        }

        if (RESERVED_EVENT_NAMES.contains(eventName)) {
            throw new APIException("RESERVED_EVENT_NAME", "Event name is reserved: " + eventName);
        }
    }

    public void validateCallback(Value callback) {
        if (callback == null) {
            throw new APIException("INVALID_CALLBACK", "Callback cannot be null");
        }

        if (!callback.canExecute()) {
            throw new APIException("INVALID_CALLBACK", "Callback must be executable");
        }
    }

    public void validateString(String value, String fieldName) {
        if (value == null) {
            throw new APIException("INVALID_STRING", "Field '" + fieldName + "' cannot be null");
        }

        if (value.length() > MAX_STRING_LENGTH) {
            throw new APIException("INVALID_STRING", "Field '" + fieldName + "' too long: max " + MAX_STRING_LENGTH + " characters");
        }
    }

    public void validateCoordinates(double x, double y, double z) {
        if (!isValidCoordinate(x) || !isValidCoordinate(y) || !isValidCoordinate(z)) {
            throw new APIException("INVALID_COORDINATES", "Invalid coordinates: " + x + ", " + y + ", " + z);
        }
    }

    public void validateBlockId(String blockId) {
        if (blockId == null || blockId.trim().isEmpty()) {
            throw new APIException("INVALID_BLOCK_ID", "Block ID cannot be null or empty");
        }

        if (!blockId.contains(":")) {
            throw new APIException("INVALID_BLOCK_ID", "Block ID must be namespaced (e.g., 'minecraft:stone')");
        }
    }

    private boolean isValidCoordinate(double coordinate) {
        return !Double.isNaN(coordinate) && !Double.isInfinite(coordinate) &&
                coordinate >= -30000000 && coordinate <= 30000000;
    }

    public void validateNotNull(Value options, String options1) {
    }
}