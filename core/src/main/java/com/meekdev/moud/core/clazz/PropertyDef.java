package com.meekdev.moud.core.clazz;

import com.meekdev.moud.core.instance.Instance;
import java.lang.invoke.VarHandle;

public final class PropertyDef {

    private final String name;
    private final PropertyType type;
    private final int index;
    private final boolean replicated;
    private final boolean driven;
    private final boolean asset;
    private final Object defaultValue;
    private final double min;
    private final double max;
    private final VarHandle handle;

    private PropertyDef(String name, PropertyType type, int index, boolean replicated, boolean driven,
                        boolean asset, Object defaultValue, double min, double max, VarHandle handle) {
        this.name = name;
        this.type = type;
        this.index = index;
        this.replicated = replicated;
        this.driven = driven;
        this.asset = asset;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.handle = handle;
    }

    static PropertyDef of(String name, PropertyType type, int index, boolean replicated, boolean driven,
                          boolean asset, Object defaultValue, double min, double max, VarHandle handle) {
        return new PropertyDef(name, type, index, replicated, driven, asset, defaultValue, min, max, handle);
    }

    public String name() { return name; }
    public PropertyType type() { return type; }
    public boolean replicated() { return replicated; }
    public boolean driven() { return driven; }
    public boolean asset() { return asset; }
    public Object defaultValue() { return defaultValue; }

    public int index() { return index; }

    public double getNum(Instance i) {
        return type == PropertyType.INT ? (int) handle.get(i) : (double) handle.get(i);
    }

    public boolean getBool(Instance i) {
        return (boolean) handle.get(i);
    }

    public Object getObj(Instance i) {
        return handle.get(i);
    }

    public void writeNum(Instance i, double v) {
        if (type == PropertyType.INT) handle.set(i, (int) v); else handle.set(i, v);
    }

    public void writeBool(Instance i, boolean v) {
        handle.set(i, v);
    }

    public void writeObj(Instance i, Object v) {
        handle.set(i, v);
    }

    public double clamp(double v) {
        return v < min ? min : (v > max ? max : v);
    }

    public boolean isNumeric() {
        return type == PropertyType.NUM || type == PropertyType.INT;
    }

    @Override
    public String toString() {
        return name + ":" + type;
    }
}
