package com.moud.core.interp;

public enum InterpProperty {
    X("x"),
    Y("y"),
    Z("z"),
    RX("rx"),
    RY("ry"),
    RZ("rz"),
    SX("sx"),
    SY("sy"),
    SZ("sz");

    public static final int COUNT = values().length;

    private final String key;

    InterpProperty(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean isPosition() {
        return this == X || this == Y || this == Z;
    }

    public boolean isRotation() {
        return this == RX || this == RY || this == RZ;
    }

    public boolean isScale() {
        return this == SX || this == SY || this == SZ;
    }

    public static InterpProperty fromKey(String key) {
        if (key == null || key.length() < 1 || key.length() > 2) {
            return null;
        }
        return switch (key) {
            case "x" -> X;
            case "y" -> Y;
            case "z" -> Z;
            case "rx" -> RX;
            case "ry" -> RY;
            case "rz" -> RZ;
            case "sx" -> SX;
            case "sy" -> SY;
            case "sz" -> SZ;
            default -> null;
        };
    }
}
