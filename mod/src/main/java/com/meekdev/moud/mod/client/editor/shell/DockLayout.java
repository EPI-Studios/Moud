package com.meekdev.moud.mod.client.editor.shell;

import com.meekdev.moud.mod.client.editor.panel.ExplorerPanel;
import com.meekdev.moud.mod.client.editor.panel.OutputPanel;
import com.meekdev.moud.mod.client.editor.panel.PropertiesPanel;
import com.meekdev.moud.mod.client.editor.viewport.ViewportPanel;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiDir;
import imgui.internal.ImGui;
import imgui.internal.flag.ImGuiDockNodeFlags;
import imgui.type.ImInt;

final class DockLayout {

    private static final String DOCKSPACE_ID = "MoudDockSpace";
    private static final float LEFT_RATIO = 0.18f;
    private static final float RIGHT_RATIO = 0.24f;
    private static final float BOTTOM_RATIO = 0.28f;

    private boolean requested = true;

    void requestDefault() {
        requested = true;
    }

    int dockspaceId() {
        return ImGui.getID(DOCKSPACE_ID);
    }

    void buildIfRequested(ImGuiViewport viewport) {
        if (!requested) return;
        requested = false;
        int root = dockspaceId();
        ImGui.dockBuilderRemoveNode(root);
        ImGui.dockBuilderAddNode(root, ImGuiDockNodeFlags.DockSpace);
        ImGui.dockBuilderSetNodeSize(root, viewport.getWorkSizeX(), viewport.getWorkSizeY());
        ImInt center = new ImInt(root);
        ImInt left = new ImInt();
        ImInt right = new ImInt();
        ImInt bottom = new ImInt();
        ImGui.dockBuilderSplitNode(center.get(), ImGuiDir.Left, LEFT_RATIO, left, center);
        ImGui.dockBuilderSplitNode(center.get(), ImGuiDir.Right, RIGHT_RATIO, right, center);
        ImGui.dockBuilderSplitNode(center.get(), ImGuiDir.Down, BOTTOM_RATIO, bottom, center);
        ImGui.dockBuilderDockWindow("###" + ExplorerPanel.ID, left.get());
        ImGui.dockBuilderDockWindow("###" + PropertiesPanel.ID, right.get());
        ImGui.dockBuilderDockWindow("###" + OutputPanel.ID, bottom.get());
        ImGui.dockBuilderDockWindow("###" + ViewportPanel.ID, center.get());
        ImGui.dockBuilderFinish(root);
    }
}
