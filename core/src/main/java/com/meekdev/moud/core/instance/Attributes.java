package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;

public final class Attributes {

    public static final int MAX_NAME = 100;

    private Attributes() {}

    public static void checkName(String name) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("an attribute needs a name");
        if (name.length() > MAX_NAME) throw new IllegalArgumentException("attribute names are at most " + MAX_NAME + " characters");
        for (int n = 0; n < name.length(); n++) {
            char c = name.charAt(n);
            if (!(c == '_' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9')) {
                throw new IllegalArgumentException("attribute \"" + name + "\" may only use letters, digits and _");
            }
        }
    }

    public static Object normalize(Object value) {
        return switch (value) {
            case null -> null;
            case Boolean b -> b;
            case Double d -> d;
            case Number n -> n.doubleValue();
            case String s -> s;
            case Vector3 v -> v;
            case Color c -> c;
            case CFrame c -> c;
            case UDim2 u -> u;
            default -> throw new IllegalArgumentException("attributes hold nil, boolean, number, string, Vector3, Color, CFrame or UDim2, not "
                    + value.getClass().getSimpleName());
        };
    }
}
