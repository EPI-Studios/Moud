package com.moud.plugin.api.services.primitives;

/**
 * Material definition for primitive rendering.
 */
public class PrimitiveMaterial {
    public float r = 1.0f;
    public float g = 1.0f;
    public float b = 1.0f;
    public float a = 1.0f;
    public String texture = null;
    public boolean unlit = false;
    public boolean doubleSided = false;
    public boolean renderThroughBlocks = false;

    public PrimitiveMaterial() {}

    public PrimitiveMaterial(float r, float g, float b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public PrimitiveMaterial(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    /**
     * Creates a solid color material.
     */
    public static PrimitiveMaterial color(float r, float g, float b) {
        return new PrimitiveMaterial(r, g, b);
    }

    /**
     * Creates a solid color material with alpha.
     */
    public static PrimitiveMaterial color(float r, float g, float b, float a) {
        return new PrimitiveMaterial(r, g, b, a);
    }

    /**
     * Creates a red material.
     */
    public static PrimitiveMaterial red() {
        return new PrimitiveMaterial(1, 0, 0);
    }

    /**
     * Creates a green material.
     */
    public static PrimitiveMaterial green() {
        return new PrimitiveMaterial(0, 1, 0);
    }

    /**
     * Creates a blue material.
     */
    public static PrimitiveMaterial blue() {
        return new PrimitiveMaterial(0, 0, 1);
    }

    /**
     * Creates a yellow material.
     */
    public static PrimitiveMaterial yellow() {
        return new PrimitiveMaterial(1, 1, 0);
    }

    /**
     * Creates a white material.
     */
    public static PrimitiveMaterial white() {
        return new PrimitiveMaterial(1, 1, 1);
    }

    /**
     * Creates a black material.
     */
    public static PrimitiveMaterial black() {
        return new PrimitiveMaterial(0, 0, 0);
    }

    public PrimitiveMaterial setColor(float r, float g, float b) {
        this.r = r;
        this.g = g;
        this.b = b;
        return this;
    }

    public PrimitiveMaterial setAlpha(float a) {
        this.a = a;
        return this;
    }

    public PrimitiveMaterial setTexture(String texture) {
        this.texture = texture;
        return this;
    }

    public PrimitiveMaterial setUnlit(boolean unlit) {
        this.unlit = unlit;
        return this;
    }

    public PrimitiveMaterial setDoubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
        return this;
    }

    public PrimitiveMaterial setRenderThroughBlocks(boolean renderThroughBlocks) {
        this.renderThroughBlocks = renderThroughBlocks;
        return this;
    }

    public PrimitiveMaterial copy() {
        PrimitiveMaterial copy = new PrimitiveMaterial(r, g, b, a);
        copy.texture = texture;
        copy.unlit = unlit;
        copy.doubleSided = doubleSided;
        copy.renderThroughBlocks = renderThroughBlocks;
        return copy;
    }
}
