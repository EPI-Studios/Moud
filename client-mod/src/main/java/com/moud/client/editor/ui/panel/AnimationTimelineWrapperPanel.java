package com.moud.client.editor.ui.panel;

import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import imgui.ImGui;

public final class AnimationTimelineWrapperPanel {
    private final SceneEditorOverlay overlay;

    public AnimationTimelineWrapperPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public void render() {
        if (!overlay.beginDockedPanel(EditorDockingLayout.Region.ANIMATION_TIMELINE, "Animation Timeline")) {
            ImGui.end();
            return;
        }

        overlay.getTimelinePanel().renderInCurrentWindow();

        ImGui.end();
    }
}
