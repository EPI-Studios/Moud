package com.meekdev.moud.core.clazz;

public enum PropertyType {
    BOOL,
    INT,
    NUM,
    STRING,
    VEC3,
    QUAT,
    CFRAME,
    COLOR,
    ASSET,
    ENUM,
    REF;

    public boolean isBool() {
        return this == BOOL;
    }
}
