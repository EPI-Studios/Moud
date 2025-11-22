package com.moud.client.editor.ui.panel;

import com.moud.client.editor.scene.SceneEditorDiagnostics;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import imgui.ImGui;
import imgui.type.ImBoolean;

import java.util.List;


public final class DiagnosticsPanel {
    private final SceneEditorOverlay overlay;

    public DiagnosticsPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public boolean render(boolean visible) {
        ImBoolean open = new ImBoolean(visible);
        if (!overlay.beginDockedPanel(EditorDockingLayout.Region.DIAGNOSTICS, "Diagnostics", open)) {
            ImGui.end();
            return open.get();
        }
        if (ImGui.button("Clear##diag_clear")) {
            SceneEditorDiagnostics.clear();
        }
        ImGui.sameLine();
        if (ImGui.button("Copy##diag_copy")) {
            List<String> lines = SceneEditorDiagnostics.snapshot();
            StringBuilder builder = new StringBuilder();
            for (String line : lines) {
                builder.append(line).append('\n');
            }
            ImGui.setClipboardText(builder.toString());
        }
        ImGui.beginChild("diagnostic-scroll", 0, ImGui.getContentRegionAvailY(), true);
        for (String line : SceneEditorDiagnostics.snapshot()) {
            ImGui.textWrapped(line);
        }
        ImGui.endChild();
        ImGui.end();
        return open.get();
    }
}
