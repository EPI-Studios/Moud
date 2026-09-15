package com.meekdev.moud.mod.client.editor.viewport;

import imgui.extension.imguizmo.flag.Mode;
import imgui.extension.imguizmo.flag.Operation;
import java.util.Optional;

public final class GizmoState {

    public enum Tool { SELECT, TRANSLATE, ROTATE, SCALE }

    public static final float TRANSLATE_SNAP_STEP = 0.5f;
    public static final float ROTATE_SNAP_STEP_DEGREES = 15.0f;
    public static final float SCALE_SNAP_STEP = 0.1f;

    private Tool tool = Tool.TRANSLATE;
    private boolean worldSpace = true;
    private boolean snapEnabled = true;
    private float gridStep = 1.0f;
    private float angleStep = ROTATE_SNAP_STEP_DEGREES;

    public Tool tool() {
        return tool;
    }

    public void setTool(Tool tool) {
        this.tool = tool;
    }

    public void toggleAlternateTool() {
        switch (tool) {
            case TRANSLATE -> tool = Tool.SCALE;
            case SCALE -> tool = Tool.TRANSLATE;
            case ROTATE -> toggleSpace();
            case SELECT -> tool = Tool.TRANSLATE;
        }
    }

    public boolean worldSpace() {
        return worldSpace;
    }

    public void toggleSpace() {
        worldSpace = !worldSpace;
    }

    public boolean snapEnabled() {
        return snapEnabled;
    }

    public void toggleSnap() {
        snapEnabled = !snapEnabled;
    }

    public Optional<Integer> operation() {
        return switch (tool) {
            case SELECT -> Optional.empty();
            case TRANSLATE -> Optional.of(Operation.TRANSLATE);
            case ROTATE -> Optional.of(Operation.ROTATE);
            case SCALE -> Optional.of(Operation.SCALE);
        };
    }

    public int mode() {
        return worldSpace && tool != Tool.SCALE ? Mode.WORLD : Mode.LOCAL;
    }

    public float snapStep() {
        return tool == Tool.ROTATE ? angleStep : gridStep;
    }

    public float gridStep() {
        return gridStep;
    }

    public void gridStep(float step) {
        gridStep = step;
    }

    public float angleStep() {
        return angleStep;
    }

    public void angleStep(float step) {
        angleStep = step;
    }
}
