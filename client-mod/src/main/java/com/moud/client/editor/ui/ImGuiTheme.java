package com.moud.client.editor.ui;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;

public final class ImGuiTheme {

    public static final float ACCENT_R = 0.26f;
    public static final float ACCENT_G = 0.59f;
    public static final float ACCENT_B = 0.98f;

    public static final float WARNING_R = 0.90f;
    public static final float WARNING_G = 0.70f;
    public static final float WARNING_B = 0.00f;

    private static final float[] COL_ACCENT = new float[]{ACCENT_R, ACCENT_G, ACCENT_B, 1.00f};
    private static final float[] COL_ACCENT_HOVER = new float[]{0.35f, 0.65f, 1.00f, 1.00f};
    private static final float[] COL_ACCENT_ACTIVE = new float[]{ACCENT_R, ACCENT_G, ACCENT_B, 1.00f};

    private static final float[] COL_SUCCESS = rgba(60, 180, 115, 255);
    private static final float[] COL_WARNING = rgba(235, 170, 50, 255);
    private static final float[] COL_DANGER = rgba(215, 60, 75, 255);

    private ImGuiTheme() {
    }

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

        style.setColor(ImGuiCol.CheckMark, ACCENT_R, ACCENT_G, ACCENT_B, 1.00f);
        style.setColor(ImGuiCol.SliderGrab, ACCENT_R, ACCENT_G, ACCENT_B, 1.00f);
        style.setColor(ImGuiCol.SliderGrabActive, 0.35f, 0.65f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.ResizeGrip, ACCENT_R, ACCENT_G, ACCENT_B, 0.20f);
        style.setColor(ImGuiCol.ResizeGripHovered, ACCENT_R, ACCENT_G, ACCENT_B, 0.60f);
        style.setColor(ImGuiCol.ResizeGripActive, ACCENT_R, ACCENT_G, ACCENT_B, 0.90f);

        style.setColor(ImGuiCol.Tab, 0.11f, 0.11f, 0.12f, 1.00f);
        style.setColor(ImGuiCol.TabHovered, 0.22f, 0.22f, 0.24f, 1.00f);
        style.setColor(ImGuiCol.TabActive, 0.27f, 0.27f, 0.29f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocused, 0.11f, 0.11f, 0.12f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocusedActive, 0.18f, 0.18f, 0.19f, 1.00f);

        style.setColor(ImGuiCol.DockingPreview, ACCENT_R, ACCENT_G, ACCENT_B, 0.30f);
        style.setColor(ImGuiCol.DockingEmptyBg, 0.08f, 0.08f, 0.09f, 1.00f);

        style.setColor(ImGuiCol.Header, 0.18f, 0.18f, 0.19f, 1.00f);
        style.setColor(ImGuiCol.HeaderHovered, 0.22f, 0.22f, 0.24f, 1.00f);
        style.setColor(ImGuiCol.HeaderActive, 0.24f, 0.24f, 0.26f, 1.00f);

        style.setColor(ImGuiCol.Text, 0.86f, 0.86f, 0.86f, 1.00f);
        style.setColor(ImGuiCol.TextDisabled, 0.47f, 0.47f, 0.47f, 1.00f);

        style.setColor(ImGuiCol.Border, 0.16f, 0.16f, 0.17f, 1.00f);
        style.setColor(ImGuiCol.BorderShadow, 0.00f, 0.00f, 0.00f, 0.00f);
        style.setColor(ImGuiCol.Separator, 0.16f, 0.16f, 0.17f, 1.00f);
        style.setColor(ImGuiCol.SeparatorHovered, ACCENT_R, ACCENT_G, ACCENT_B, 0.78f);
        style.setColor(ImGuiCol.SeparatorActive, ACCENT_R, ACCENT_G, ACCENT_B, 1.00f);

        style.setColor(ImGuiCol.ScrollbarBg, 0.08f, 0.08f, 0.09f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab, 0.24f, 0.24f, 0.26f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, 0.28f, 0.28f, 0.30f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabActive, 0.31f, 0.31f, 0.35f, 1.00f);

        style.setColor(ImGuiCol.PlotLines, 0.61f, 0.61f, 0.61f, 1.00f);
        style.setColor(ImGuiCol.PlotLinesHovered, 1.00f, 0.43f, 0.35f, 1.00f);
        style.setColor(ImGuiCol.PlotHistogram, WARNING_R, WARNING_G, WARNING_B, 1.00f);
        style.setColor(ImGuiCol.PlotHistogramHovered, 1.00f, 0.60f, 0.00f, 1.00f);

        style.setColor(ImGuiCol.DragDropTarget, 1.00f, 1.00f, 0.00f, 0.90f);
        style.setColor(ImGuiCol.NavHighlight, ACCENT_R, ACCENT_G, ACCENT_B, 1.00f);
    }

    public static void pushAccentButtonStyle() {
        ImGui.pushStyleColor(ImGuiCol.Button, COL_ACCENT[0], COL_ACCENT[1], COL_ACCENT[2], COL_ACCENT[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, COL_ACCENT_HOVER[0], COL_ACCENT_HOVER[1], COL_ACCENT_HOVER[2], COL_ACCENT_HOVER[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, COL_ACCENT_ACTIVE[0], COL_ACCENT_ACTIVE[1], COL_ACCENT_ACTIVE[2], COL_ACCENT_ACTIVE[3]);
        ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void popAccentButtonStyle() {
        ImGui.popStyleColor(4);
    }

    public static void pushSuccessButtonStyle() {
        pushColoredButton(COL_SUCCESS);
    }

    public static void popSuccessButtonStyle() {
        popButtonStyle();
    }

    public static void pushDangerButtonStyle() {
        pushColoredButton(COL_DANGER);
    }

    public static void popDangerButtonStyle() {
        popButtonStyle();
    }

    public static void pushWarningButtonStyle() {
        pushColoredButton(COL_WARNING);
    }

    public static void popWarningButtonStyle() {
        popButtonStyle();
    }

    public static void popButtonStyle() {
        ImGui.popStyleColor(3);
    }

    public static void pushSectionHeaderStyle() {
        ImGui.pushStyleColor(ImGuiCol.Text, ACCENT_R, ACCENT_G, ACCENT_B, 1.00f);
    }

    public static void popSectionHeaderStyle() {
        ImGui.popStyleColor(1);
    }

    private static void pushColoredButton(float[] color) {
        ImGui.pushStyleColor(ImGuiCol.Button, color[0], color[1], color[2], color[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, color[0] * 1.1f, color[1] * 1.1f, color[2] * 1.1f, color[3]);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, color[0] * 0.9f, color[1] * 0.9f, color[2] * 0.9f, color[3]);
    }

    private static float[] rgba(int r, int g, int b, int a) {
        return new float[]{
                (float) r / 255.0f,
                (float) g / 255.0f,
                (float) b / 255.0f,
                (float) a / 255.0f
        };
    }
}
