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
    private boolean snapEnabled;

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
        return switch (tool) {
            case ROTATE -> ROTATE_SNAP_STEP_DEGREES;
            case SCALE -> SCALE_SNAP_STEP;
            default -> TRANSLATE_SNAP_STEP;
        };
    }
}
