package com.meekdev.moud.core.ui;

public enum ScrollingDirection {
    X,
    Y,
    XY;

    public boolean x() {
        return this != Y;
    }

    public boolean y() {
        return this != X;
    }
}
