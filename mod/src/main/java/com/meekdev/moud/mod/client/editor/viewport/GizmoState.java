package com.meekdev.moud.mod.client.editor.viewport;

import imgui.extension.imguizmo.flag.Mode;
import imgui.extension.imguizmo.flag.Operation;
import java.util.Optional;

public final class GizmoState {

    public enum Tool { SELECT, TRANSLATE, ROTATE, SCALE, PIVOT, PAINT, CONSTRAINT }

    public enum Space { WORLD, LOCAL, PARENT }

    public static final float TRANSLATE_SNAP_STEP = 0.5f;
    public static final float ROTATE_SNAP_STEP_DEGREES = 15.0f;
    public static final float SCALE_SNAP_STEP = 0.1f;

    private Tool tool = Tool.TRANSLATE;
    private Space space = Space.WORLD;
    private boolean individualPivots;
    private boolean lasso;
    private float[] brush = {0.9f, 0.3f, 0.3f, 1.0f};
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
            case SELECT, PIVOT, PAINT, CONSTRAINT -> tool = Tool.TRANSLATE;
        }
    }

    public boolean worldSpace() {
        return space == Space.WORLD;
    }

    public Space space() {
        return space;
    }

    public void toggleSpace() {
        space = Space.values()[(space.ordinal() + 1) % Space.values().length];
    }

    public boolean individualPivots() {
        return individualPivots;
    }

    public void toggleIndividualPivots() {
        individualPivots = !individualPivots;
    }

    public boolean lasso() {
        return lasso;
    }

    public void toggleLasso() {
        lasso = !lasso;
    }

    public float[] brush() {
        return brush;
    }

    public boolean snapEnabled() {
        return snapEnabled;
    }

    public void toggleSnap() {
        snapEnabled = !snapEnabled;
    }

    public Optional<Integer> operation() {
        return switch (tool) {
            case SELECT, PAINT, CONSTRAINT -> Optional.empty();
            case PIVOT -> Optional.of(Operation.TRANSLATE);
            case TRANSLATE -> Optional.of(Operation.TRANSLATE);
            case ROTATE -> Optional.of(Operation.ROTATE);
            case SCALE -> Optional.of(Operation.SCALE);
        };
    }

    public int mode() {
        return space == Space.WORLD && tool != Tool.SCALE ? Mode.WORLD : Mode.LOCAL;
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
