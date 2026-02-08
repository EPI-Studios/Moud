package com.moud.client.editor.ui.panel.timeline;

public record TransformSnapshot(float[] translation, float[] rotation, float[] scale) {
    static TransformSnapshot from(float[] translation, float[] rotation, float[] scale) {
        return new TransformSnapshot(translation.clone(), rotation.clone(), scale.clone());
    }
}
