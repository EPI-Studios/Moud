package com.moud.client.fabric.editor.widgets;

public record CurvePoint(float x, float y) {
    public CurvePoint with(float nx, float ny) { return new CurvePoint(nx, ny); }
}
