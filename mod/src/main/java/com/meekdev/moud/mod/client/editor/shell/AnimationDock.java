package com.meekdev.moud.mod.client.editor.shell;

import com.meekdev.moud.mod.client.editor.animation.AnimationWorkspace;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import imgui.flag.ImGuiDir;
import imgui.internal.ImGui;
import imgui.internal.flag.ImGuiDockNodeFlags;
import imgui.type.ImInt;

final class AnimationDock {

    private static final String DOCKSPACE_ID = "MoudAnimationDockSpace";
    private static final float LEFT_WIDTH = 250.0f;
    private static final float RIGHT_WIDTH = 310.0f;
    private static final float BOTTOM_HEIGHT = 300.0f;
    private static final float MAX_SIDE_SHARE = 0.3f;
    private static final float RESIZE_TOLERANCE = 0.2f;

    private boolean requested = true;
    private float builtWidth;
    private float builtHeight;

    void requestDefault() {
        requested = true;
    }

    int dockspaceId() {
        return ImGui.getID(DOCKSPACE_ID);
    }

    void buildIfRequested(AnimationWorkspace animation, float width, float height) {
        if (builtWidth > 0 && (Math.abs(width - builtWidth) > builtWidth * RESIZE_TOLERANCE || Math.abs(height - builtHeight) > builtHeight * RESIZE_TOLERANCE)) {
            requested = true;
        }
        if (!requested || width <= 0 || height <= 0) return;
        requested = false;
        builtWidth = width;
        builtHeight = height;
        int root = dockspaceId();
        ImGui.dockBuilderRemoveNode(root);
        ImGui.dockBuilderAddNode(root, ImGuiDockNodeFlags.DockSpace);
        ImGui.dockBuilderSetNodeSize(root, width, height);
        ImInt centre = new ImInt(root);
        ImInt left = new ImInt();
        ImInt right = new ImInt();
        ImInt bottom = new ImInt();
        ImGui.dockBuilderSplitNode(centre.get(), ImGuiDir.Down, Math.min(0.45f, EditorScale.of(BOTTOM_HEIGHT) / height), bottom, centre);
        float leftShare = Math.min(MAX_SIDE_SHARE, EditorScale.of(LEFT_WIDTH) / width);
        ImGui.dockBuilderSplitNode(centre.get(), ImGuiDir.Left, leftShare, left, centre);
        ImGui.dockBuilderSplitNode(centre.get(), ImGuiDir.Right, Math.min(MAX_SIDE_SHARE, EditorScale.of(RIGHT_WIDTH) / (width * (1.0f - leftShare))), right, centre);
        ImGui.dockBuilderDockWindow("###" + animation.rigId(), left.get());
        ImGui.dockBuilderDockWindow("###" + animation.inspectorId(), right.get());
        ImGui.dockBuilderDockWindow("###" + animation.timelineId(), bottom.get());
        ImGui.dockBuilderDockWindow("###" + animation.viewportId(), centre.get());
        ImGui.dockBuilderFinish(root);
    }
}
