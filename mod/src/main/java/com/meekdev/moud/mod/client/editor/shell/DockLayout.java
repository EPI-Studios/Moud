package com.meekdev.moud.mod.client.editor.shell;

import com.meekdev.moud.mod.client.editor.assets.AssetsPanel;
import com.meekdev.moud.mod.client.editor.panel.ExplorerPanel;
import com.meekdev.moud.mod.client.editor.panel.OutputPanel;
import com.meekdev.moud.mod.client.editor.panel.PropertiesPanel;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.viewport.ViewportPanel;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiDir;
import imgui.internal.ImGui;
import imgui.internal.flag.ImGuiDockNodeFlags;
import imgui.type.ImInt;

final class DockLayout {

    private static final String DOCKSPACE_ID = "MoudDockSpace";
    private static final float LEFT_WIDTH = 280.0f;
    private static final float RIGHT_WIDTH = 360.0f;
    private static final float BOTTOM_HEIGHT = 220.0f;
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

    void buildIfRequested(ImGuiViewport viewport) {
        float width = viewport.getWorkSizeX();
        float height = viewport.getWorkSizeY();
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
        ImGui.dockBuilderSetNodeSize(root, viewport.getWorkSizeX(), viewport.getWorkSizeY());
        ImInt center = new ImInt(root);
        ImInt left = new ImInt();
        ImInt right = new ImInt();
        ImInt bottom = new ImInt();
        ImGui.dockBuilderSplitNode(center.get(), ImGuiDir.Left, Math.min(MAX_SIDE_SHARE, EditorScale.of(LEFT_WIDTH) / width), left, center);
        ImGui.dockBuilderSplitNode(center.get(), ImGuiDir.Right, Math.min(MAX_SIDE_SHARE, EditorScale.of(RIGHT_WIDTH) / (width * (1.0f - Math.min(MAX_SIDE_SHARE, EditorScale.of(LEFT_WIDTH) / width)))), right, center);
        ImGui.dockBuilderSplitNode(center.get(), ImGuiDir.Down, Math.min(0.4f, EditorScale.of(BOTTOM_HEIGHT) / height), bottom, center);
        ImGui.dockBuilderDockWindow("###" + ExplorerPanel.ID, left.get());
        ImGui.dockBuilderDockWindow("###" + PropertiesPanel.ID, right.get());
        ImGui.dockBuilderDockWindow("###" + OutputPanel.ID, bottom.get());
        ImGui.dockBuilderDockWindow("###" + AssetsPanel.ID, bottom.get());
        ImGui.dockBuilderDockWindow("###" + ViewportPanel.ID, center.get());
        ImGui.dockBuilderFinish(root);
    }
}
