package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.scene.visual.VisualTransformRegistry;

public final class VisualApi {

    public void setOffset(long nodeId, double dx, double dy, double dz) {
        VisualTransformRegistry.get().setOffset(nodeId, (float) dx, (float) dy, (float) dz);
    }

    public void setRotation(long nodeId, double rxDeg, double ryDeg, double rzDeg) {
        VisualTransformRegistry.get().setRotation(nodeId, (float) rxDeg, (float) ryDeg, (float) rzDeg);
    }

    public void setScale(long nodeId, double sxMul, double syMul, double szMul) {
        VisualTransformRegistry.get().setScale(nodeId, (float) sxMul, (float) syMul, (float) szMul);
    }

    public void clear(long nodeId) {
        VisualTransformRegistry.get().clearNode(nodeId);
    }
}
