package com.meekdev.moud.core.instance;

public enum Stage {

    SHAPE,

    DRIVE,

    EVALUATE,

    SIMULATE,

    COMPOSE;

    public static final Stage[] ORDER = values();

    public final int bit = 1 << ordinal();

    public String method() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean timed() {
        return this == DRIVE || this == SIMULATE;
    }
}
