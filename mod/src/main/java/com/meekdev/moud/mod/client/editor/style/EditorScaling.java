package com.meekdev.moud.mod.client.editor.style;

import imgui.ImGui;
import imgui.ImGuiStyle;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

public final class EditorScaling {

    private static final float GUI_SCALE_PER_FACTOR = 2.0f;
    private static final float HEIGHT_PER_FACTOR = 760.0f;
    private static final float STEPS = 4.0f;

    private EditorScaling() {}

    public static float begin() {
        Window window = Minecraft.getInstance().getWindow();
        float byGui = window.getGuiScale() / GUI_SCALE_PER_FACTOR;
        float byHeight = window.getHeight() / HEIGHT_PER_FACTOR;
        EditorScale.setFactor(Math.round(Math.max(1.0f, Math.max(byGui, byHeight)) * STEPS) / STEPS);
        ImGuiStyle style = ImGui.getStyle();
        float previous = style.getFontScaleMain();
        style.setFontScaleMain(1.0f);
        return previous;
    }

    public static void end(float previous) {
        ImGui.getStyle().setFontScaleMain(previous);
    }
}
