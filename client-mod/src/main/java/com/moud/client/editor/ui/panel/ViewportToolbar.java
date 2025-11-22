package com.moud.client.editor.ui.panel;

import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.extension.imguizmo.flag.Mode;
import imgui.extension.imguizmo.flag.Operation;

public final class ViewportToolbar {
    private final SceneEditorOverlay overlay;
    private final ImBoolean snapToggle = new ImBoolean(false);

    public ViewportToolbar(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public void render(EditorDockingLayout.Rect viewportRect) {
        float x = 320f;
        float y = 64f;
        if (viewportRect != null) {
            x = Math.max(viewportRect.x + viewportRect.width - 360f, viewportRect.x + 16f);
            y = viewportRect.y + 16f;
        }
        ImGui.setNextWindowPos(x, y);
        ImGui.setNextWindowSize(420, 110, ImGuiCond.Always);
        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize;
        if (!ImGui.begin("Gizmo Controls", flags)) {
            ImGui.end();
            return;
        }
        renderOperationSection();
        renderModeSection();
        renderSnapSection();
        ImGui.textDisabled("Hold F to fly the camera");
        ImGui.end();
    }

    private void renderOperationSection() {
        ImGui.textDisabled("Gizmo mode");
        int operation = overlay.getCurrentGizmoOperation();
        if (ImGui.radioButton("Translate", operation == Operation.TRANSLATE)) {
            overlay.setCurrentGizmoOperation(Operation.TRANSLATE);
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Rotate", operation == Operation.ROTATE)) {
            overlay.setCurrentGizmoOperation(Operation.ROTATE);
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Scale", operation == Operation.SCALE)) {
            overlay.setCurrentGizmoOperation(Operation.SCALE);
        }
    }

    private void renderModeSection() {
        if (overlay.getCurrentGizmoOperation() == Operation.SCALE) {
            return;
        }
        int mode = overlay.getGizmoMode();
        if (ImGui.radioButton("Local", mode == Mode.LOCAL)) {
            overlay.setGizmoMode(Mode.LOCAL);
        }
        ImGui.sameLine();
        if (ImGui.radioButton("World", mode == Mode.WORLD)) {
            overlay.setGizmoMode(Mode.WORLD);
        }
    }

    private void renderSnapSection() {
        snapToggle.set(overlay.isSnapEnabled());
        if (ImGui.checkbox("Snap", snapToggle)) {
            overlay.setSnapEnabled(snapToggle.get());
        }
        if (!overlay.isSnapEnabled()) {
            return;
        }
        float[] snapValues = overlay.getSnapValues();
        if (overlay.getCurrentGizmoOperation() == Operation.ROTATE) {
            ImFloat angle = new ImFloat(snapValues[0]);
            if (ImGui.inputFloat("Angle Snap", angle)) {
                float value = Math.max(1f, angle.get());
                snapValues[0] = snapValues[1] = snapValues[2] = value;
            }
        } else if (ImGui.inputFloat3("Snap XYZ", snapValues)) {
            snapValues[0] = Math.max(0.1f, snapValues[0]);
            snapValues[1] = Math.max(0.1f, snapValues[1]);
            snapValues[2] = Math.max(0.1f, snapValues[2]);
        }
    }
}
