package com.moud.plugin.api.world;

import com.moud.api.math.Vector3;

public final class DisplayAnchor {
    public enum Type {
        FREE, BLOCK, ENTITY, PLAYER
    }

    private Type type = Type.FREE;
    private int x;
    private int y;
    private int z;
    private String uuid;
    private Vector3 offset = Vector3.zero();

    public Type type() {
        return type;
    }

    public DisplayAnchor type(Type type) {
        this.type = type;
        return this;
    }

    public int x() {
        return x;
    }

    public DisplayAnchor x(int x) {
        this.x = x;
        return this;
    }

    public int y() {
        return y;
    }

    public DisplayAnchor y(int y) {
        this.y = y;
        return this;
    }

    public int z() {
        return z;
    }

    public DisplayAnchor z(int z) {
        this.z = z;
        return this;
    }

    public String uuid() {
        return uuid;
    }

    public DisplayAnchor uuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    public Vector3 offset() {
        return offset;
    }

    public DisplayAnchor offset(Vector3 offset) {
        this.offset = offset;
        return this;
    }
}
