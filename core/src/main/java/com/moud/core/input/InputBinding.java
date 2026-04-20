package com.moud.core.input;

public record InputBinding(Kind kind, int code) {

    public enum Kind {
        KEY,
        MOUSE_BUTTON,
        GAMEPAD_BUTTON
    }

    public static InputBinding key(int keyCode) {
        return new InputBinding(Kind.KEY, keyCode);
    }

    public static InputBinding mouse(int button) {
        return new InputBinding(Kind.MOUSE_BUTTON, button);
    }

    public static InputBinding gamepad(int button) {
        return new InputBinding(Kind.GAMEPAD_BUTTON, button);
    }

    public String toToken() {
        return switch (kind) {
            case KEY -> "key:" + code;
            case MOUSE_BUTTON -> "mouse:" + code;
            case GAMEPAD_BUTTON -> "gamepad:" + code;
        };
    }

    public static InputBinding parse(String token) {
        if (token == null) return null;
        int colon = token.indexOf(':');
        if (colon <= 0 || colon == token.length() - 1) return null;
        String prefix = token.substring(0, colon).trim().toLowerCase();
        int code;
        try {
            code = Integer.parseInt(token.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return switch (prefix) {
            case "key" -> key(code);
            case "mouse" -> mouse(code);
            case "gamepad" -> gamepad(code);
            default -> null;
        };
    }
}
