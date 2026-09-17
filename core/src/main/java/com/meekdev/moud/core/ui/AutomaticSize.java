package com.meekdev.moud.core.ui;

public enum AutomaticSize {
    NONE,
    X,
    Y,
    XY;

    public boolean x() {
        return this == X || this == XY;
    }

    public boolean y() {
        return this == Y || this == XY;
    }
}
