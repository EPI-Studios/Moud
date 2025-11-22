package com.moud.client.editor.ui.panel;

import com.moud.client.editor.assets.ProjectFileContentCache;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import imgui.ImGui;

import java.util.Locale;
import java.util.Map;

public final class ScriptViewerPanel {
    private final SceneEditorOverlay overlay;
    private String activeScriptPath;
    private String lastObjectId;

    public ScriptViewerPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public void render(SceneSessionManager session) {
        if (!overlay.beginDockedPanel(EditorDockingLayout.Region.SCRIPT_VIEWER, "Script Viewer")) {
            ImGui.end();
            return;
        }
        SceneObject selected = overlay.getSelectedObject();
        if (selected == null) {
            ImGui.textDisabled("Select an object with a script component.");
            ImGui.end();
            return;
        }
        if (lastObjectId == null || !lastObjectId.equals(selected.getId())) {
            lastObjectId = selected.getId();
            activeScriptPath = null;
        }

        Map<String, Object> props = selected.getProperties();
        Object scriptRef = props.getOrDefault("script", props.get("behavior"));
        if (scriptRef instanceof String path && !path.isBlank()) {
            ImGui.text("Script: " + path);
            ImGui.sameLine();
            if (ImGui.button("View Source##script_view_source")) {
                activeScriptPath = path;
                ProjectFileContentCache.getInstance().request(path);
            }
            ImGui.sameLine();
            if (ImGui.button("Copy Path##script_copy_path")) {
                ImGui.setClipboardText(path);
            }
        } else {
            ImGui.textDisabled("No script binding on this object.");
        }
        ImGui.separator();
        ImGui.textDisabled("Property Preview");
        float previewHeight = Math.max(140f, ImGui.getContentRegionAvailY() * 0.4f);
        ImGui.beginChild("script-props", 0, previewHeight, true);
        boolean printed = false;
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (!entry.getKey().toLowerCase(Locale.ROOT).contains("script")) {
                continue;
            }
            ImGui.text(entry.getKey() + ": " + entry.getValue());
            printed = true;
        }
        if (!printed) {
            ImGui.textDisabled("No script-specific properties on this object.");
        }
        ImGui.endChild();
        renderSourcePreview();
        ImGui.end();
    }

    private void renderSourcePreview() {
        if (activeScriptPath == null || activeScriptPath.isBlank()) {
            return;
        }
        ImGui.separator();
        ImGui.textDisabled("Source Preview");
        ProjectFileContentCache.Entry entry = ProjectFileContentCache.getInstance().get(activeScriptPath);
        if (entry == null || entry.loading()) {
            ImGui.textDisabled("Loading " + activeScriptPath + "...");
            return;
        }
        if (!entry.success()) {
            String message = entry.message() == null ? "Unable to load source." : entry.message();
            ImGui.textDisabled(message);
            if (entry.absolutePath() != null) {
                if (ImGui.button("Copy Absolute Path##script_copy_abs")) {
                    ImGui.setClipboardText(entry.absolutePath());
                }
            }
            return;
        }
        if (entry.absolutePath() != null) {
            ImGui.textDisabled(entry.absolutePath());
            ImGui.sameLine();
            if (ImGui.button("Copy##script_copy_abs_path")) {
                ImGui.setClipboardText(entry.absolutePath());
            }
        }
        ImGui.beginChild("script-source-view", 0, Math.max(160f, ImGui.getContentRegionAvailY()), true);
        ImGui.pushTextWrapPos();
        ImGui.textUnformatted(entry.content() == null ? "" : entry.content());
        ImGui.popTextWrapPos();
        ImGui.endChild();
    }
}
