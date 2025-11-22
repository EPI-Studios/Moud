package com.moud.client.editor.ui;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDir;

public final class ImGuiTheme {

    private ImGuiTheme() {}

    public static void applyIdaEngineTheme() {
        ImGuiStyle style = ImGui.getStyle();

        ImGui.styleColorsDark();

        style.setWindowRounding(0.0f);
        style.setChildRounding(0.0f);
        style.setPopupRounding(3.0f);
        style.setFrameRounding(2.0f);
        style.setScrollbarRounding(2.0f);
        style.setGrabRounding(2.0f);
        style.setTabRounding(2.0f);

        style.setWindowPadding(8.0f, 8.0f);
        style.setFramePadding(8.0f, 4.0f);
        style.setItemSpacing(8.0f, 6.0f);
        style.setItemInnerSpacing(6.0f, 6.0f);
        style.setIndentSpacing(20.0f);
        style.setScrollbarSize(14.0f);

        style.setColor(ImGuiCol.WindowBg, 0.08f, 0.08f, 0.09f, 1.00f);

        style.setColor(ImGuiCol.ChildBg, 0.11f, 0.11f, 0.12f, 1.00f);
        style.setColor(ImGuiCol.PopupBg, 0.14f, 0.14f, 0.15f, 1.00f);

        style.setColor(ImGuiCol.TitleBg, 0.11f, 0.11f, 0.12f, 1.00f);
        style.setColor(ImGuiCol.TitleBgActive, 0.18f, 0.18f, 0.19f, 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed, 0.11f, 0.11f, 0.12f, 1.00f);
        style.setColor(ImGuiCol.MenuBarBg, 0.11f, 0.11f, 0.12f, 1.00f);

        style.setColor(ImGuiCol.FrameBg, 0.06f, 0.06f, 0.07f, 1.00f);
        style.setColor(ImGuiCol.FrameBgHovered, 0.16f, 0.16f, 0.17f, 1.00f);
        style.setColor(ImGuiCol.FrameBgActive, 0.24f, 0.24f, 0.26f, 1.00f);

        style.setColor(ImGuiCol.Button, 0.18f, 0.18f, 0.19f, 1.00f);
        style.setColor(ImGuiCol.ButtonHovered, 0.24f, 0.24f, 0.26f, 1.00f);
        style.setColor(ImGuiCol.ButtonActive, 0.31f, 0.31f, 0.35f, 1.00f);

        float accentR = 0.26f;
        float accentG = 0.59f;
        float accentB = 0.98f;

        style.setColor(ImGuiCol.CheckMark, accentR, accentG, accentB, 1.00f);
        style.setColor(ImGuiCol.SliderGrab, accentR, accentG, accentB, 1.00f);
        style.setColor(ImGuiCol.SliderGrabActive, 0.35f, 0.65f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.ResizeGrip, accentR, accentG, accentB, 0.20f);
        style.setColor(ImGuiCol.ResizeGripHovered, accentR, accentG, accentB, 0.60f);
        style.setColor(ImGuiCol.ResizeGripActive, accentR, accentG, accentB, 0.90f);

        style.setColor(ImGuiCol.Tab, 0.11f, 0.11f, 0.12f, 1.00f);
        style.setColor(ImGuiCol.TabHovered, 0.22f, 0.22f, 0.24f, 1.00f);
        style.setColor(ImGuiCol.TabActive, 0.27f, 0.27f, 0.29f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocused, 0.11f, 0.11f, 0.12f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocusedActive, 0.18f, 0.18f, 0.19f, 1.00f);

        style.setColor(ImGuiCol.DockingPreview, accentR, accentG, accentB, 0.30f);
        style.setColor(ImGuiCol.DockingEmptyBg, 0.08f, 0.08f, 0.09f, 1.00f);

        style.setColor(ImGuiCol.Header, 0.18f, 0.18f, 0.19f, 1.00f);
        style.setColor(ImGuiCol.HeaderHovered, 0.22f, 0.22f, 0.24f, 1.00f);
        style.setColor(ImGuiCol.HeaderActive, 0.24f, 0.24f, 0.26f, 1.00f);

        style.setColor(ImGuiCol.Text, 0.86f, 0.86f, 0.86f, 1.00f);
        style.setColor(ImGuiCol.TextDisabled, 0.47f, 0.47f, 0.47f, 1.00f);

        style.setColor(ImGuiCol.Border, 0.16f, 0.16f, 0.17f, 1.00f);
        style.setColor(ImGuiCol.BorderShadow, 0.00f, 0.00f, 0.00f, 0.00f);
        style.setColor(ImGuiCol.Separator, 0.16f, 0.16f, 0.17f, 1.00f);
        style.setColor(ImGuiCol.SeparatorHovered, 0.26f, 0.59f, 0.98f, 0.78f);
        style.setColor(ImGuiCol.SeparatorActive, 0.26f, 0.59f, 0.98f, 1.00f);

        style.setColor(ImGuiCol.ScrollbarBg, 0.08f, 0.08f, 0.09f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab, 0.24f, 0.24f, 0.26f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, 0.28f, 0.28f, 0.30f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabActive, 0.31f, 0.31f, 0.35f, 1.00f);

        style.setColor(ImGuiCol.PlotLines, 0.61f, 0.61f, 0.61f, 1.00f);
        style.setColor(ImGuiCol.PlotLinesHovered, 1.00f, 0.43f, 0.35f, 1.00f);
        style.setColor(ImGuiCol.PlotHistogram, 0.90f, 0.70f, 0.00f, 1.00f);
        style.setColor(ImGuiCol.PlotHistogramHovered, 1.00f, 0.60f, 0.00f, 1.00f);

        style.setColor(ImGuiCol.DragDropTarget, 1.00f, 1.00f, 0.00f, 0.90f);
        style.setColor(ImGuiCol.NavHighlight, 0.26f, 0.59f, 0.98f, 1.00f);
    }
}