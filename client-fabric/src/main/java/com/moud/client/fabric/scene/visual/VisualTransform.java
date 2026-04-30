package com.moud.client.fabric.scene.visual;

public final class VisualTransform {

    public static final VisualTransform IDENTITY = new VisualTransform();

    public float dx;
    public float dy;
    public float dz;
    public float rxOff;
    public float ryOff;
    public float rzOff;
    public float sxMul = 1.0f;
    public float syMul = 1.0f;
    public float szMul = 1.0f;

    public boolean isIdentity() {
        return dx == 0.0f && dy == 0.0f && dz == 0.0f
                && rxOff == 0.0f && ryOff == 0.0f && rzOff == 0.0f
                && sxMul == 1.0f && syMul == 1.0f && szMul == 1.0f;
    }

    public void copyFrom(VisualTransform src) {
        if (src == null) {
            reset();
            return;
        }
        this.dx = src.dx;
        this.dy = src.dy;
        this.dz = src.dz;
        this.rxOff = src.rxOff;
        this.ryOff = src.ryOff;
        this.rzOff = src.rzOff;
        this.sxMul = src.sxMul;
        this.syMul = src.syMul;
        this.szMul = src.szMul;
    }

    public void reset() {
        dx = dy = dz = 0.0f;
        rxOff = ryOff = rzOff = 0.0f;
        sxMul = syMul = szMul = 1.0f;
    }
}
