package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;

public final class FocusRing {

    private static final float THICKNESS = 1.0f;
    private static final float HOVER_ALPHA = 0.45f;
    private static final float ACTIVE_ALPHA = 1.0f;

    private FocusRing() {
    }

    public static void aroundLastItem(String id) {
        aroundLastItem(id, EditorStyle.frameRounding());
    }

    public static void aroundLastItem(String id, float rounding) {
        float target = ImGui.isItemActive() ? ACTIVE_ALPHA : ImGui.isItemHovered() ? HOVER_ALPHA : 0.0f;
        float alpha = EditorMotion.towards(id + "-ring", target, EditorMotion.DEFAULT_DURATION_SECONDS);
        if (alpha <= 0.0f) {
            return;
        }
        ImGui.getWindowDrawList().addRect(
                ImGui.getItemRectMinX(), ImGui.getItemRectMinY(),
                ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY(),
                EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, alpha),
                rounding, 0, EditorScale.ofAtLeastOne(THICKNESS));
    }
}
