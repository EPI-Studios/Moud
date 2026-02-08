package com.moud.plugin.api.world;

import com.moud.api.math.Vector3;

public final class DisplayAnchor {
    public enum Type {
        FREE, BLOCK, ENTITY, PLAYER, MODEL
    }

    private Type type = Type.FREE;
    private int x;
    private int y;
    private int z;
    private String uuid;
    private Long modelId;
    private Vector3 offset = Vector3.zero();
    private boolean local = false;
    private boolean inheritRotation = false;
    private boolean inheritScale = false;
    private boolean includePitch = false;

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

    public Long modelId() {
        return modelId;
    }

    public DisplayAnchor modelId(long modelId) {
        this.modelId = modelId;
        return this;
    }

    public Vector3 offset() {
        return offset;
    }

    public DisplayAnchor offset(Vector3 offset) {
        this.offset = offset;
        return this;
    }

    public boolean local() {
        return local;
    }

    public DisplayAnchor local(boolean local) {
        this.local = local;
        return this;
    }

    public boolean inheritRotation() {
        return inheritRotation;
    }

    public DisplayAnchor inheritRotation(boolean inheritRotation) {
        this.inheritRotation = inheritRotation;
        return this;
    }

    public boolean inheritScale() {
        return inheritScale;
    }

    public DisplayAnchor inheritScale(boolean inheritScale) {
        this.inheritScale = inheritScale;
        return this;
    }

    public boolean includePitch() {
        return includePitch;
    }

    public DisplayAnchor includePitch(boolean includePitch) {
        this.includePitch = includePitch;
        return this;
    }
}
