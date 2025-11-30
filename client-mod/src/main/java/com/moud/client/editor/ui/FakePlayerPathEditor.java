package com.moud.client.editor.ui;

import imgui.ImGui;
import java.util.List;

final class FakePlayerPathEditor {
    private FakePlayerPathEditor() {}

    static void render(List<float[]> waypoints, Runnable addFromCursor) {
        if (ImGui.button("Add Waypoint (cursor)")) {
            if (addFromCursor != null) {
                addFromCursor.run();
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Clear Path")) {
            waypoints.clear();
        }
        ImGui.textDisabled("Waypoints: " + waypoints.size());
        for (int i = 0; i < waypoints.size(); i++) {
            float[] p = waypoints.get(i);
            ImGui.pushID(i);
            if (ImGui.dragFloat3("wp##" + i, p, 0.1f)) {
                waypoints.set(i, new float[]{p[0], p[1], p[2]});
            }
            ImGui.sameLine();
            if (ImGui.smallButton("X##del" + i)) {
                waypoints.remove(i);
                ImGui.popID();
                break;
            }
            ImGui.popID();
        }
    }
}
