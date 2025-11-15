package com.moud.client.editor.ui;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImVec2;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
// stolen from https://github.com/shivang51/bess/blob/main/src/Bess/src/settings/themes.cpp :')
public final class ImGuiTheme {

    private ImGuiTheme() {}

    public static void applyBessDark() {
        ImGuiStyle style = ImGui.getStyle();

        // Primary background
        ImGui.styleColorsDark();
        style.setColor(ImGuiCol.WindowBg, 0.07f, 0.07f, 0.09f, 1.00f);  // #131318
        style.setColor(ImGuiCol.MenuBarBg, 0.12f, 0.12f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.PopupBg, 0.18f, 0.18f, 0.22f, 1.00f);

        // Headers
        style.setColor(ImGuiCol.Header, 0.18f, 0.18f, 0.22f, 1.00f);
        style.setColor(ImGuiCol.HeaderHovered, 0.30f, 0.30f, 0.40f, 1.00f);
        style.setColor(ImGuiCol.HeaderActive, 0.25f, 0.25f, 0.35f, 1.00f);

        // Buttons
        style.setColor(ImGuiCol.Button, 0.20f, 0.22f, 0.27f, 1.00f);
        style.setColor(ImGuiCol.ButtonHovered, 0.30f, 0.32f, 0.40f, 1.00f);
        style.setColor(ImGuiCol.ButtonActive, 0.35f, 0.38f, 0.50f, 1.00f);

        // Frame BG
        style.setColor(ImGuiCol.FrameBg, 0.15f, 0.15f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.FrameBgHovered, 0.22f, 0.22f, 0.27f, 1.00f);
        style.setColor(ImGuiCol.FrameBgActive, 0.25f, 0.25f, 0.30f, 1.00f);

        // Tabs
        style.setColor(ImGuiCol.Tab, 0.18f, 0.18f, 0.22f, 1.00f);
        style.setColor(ImGuiCol.TabHovered, 0.35f, 0.35f, 0.50f, 1.00f);
        style.setColor(ImGuiCol.TabActive, 0.25f, 0.25f, 0.38f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocused, 0.13f, 0.13f, 0.17f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocusedActive, 0.20f, 0.20f, 0.25f, 1.00f);

        // Title
        style.setColor(ImGuiCol.TitleBg, 0.12f, 0.12f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.TitleBgActive, 0.15f, 0.15f, 0.20f, 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed, 0.10f, 0.10f, 0.12f, 1.00f);

        // Borders
        style.setColor(ImGuiCol.Border, 0.20f, 0.20f, 0.25f, 0.50f);
        style.setColor(ImGuiCol.BorderShadow, 0.00f, 0.00f, 0.00f, 0.00f);

        // Text
        style.setColor(ImGuiCol.Text, 0.90f, 0.90f, 0.95f, 1.00f);
        style.setColor(ImGuiCol.TextDisabled, 0.50f, 0.50f, 0.55f, 1.00f);

        // Highlights
        style.setColor(ImGuiCol.CheckMark, 0.50f, 0.70f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.SliderGrab, 0.50f, 0.70f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.SliderGrabActive, 0.60f, 0.80f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.ResizeGrip, 0.50f, 0.70f, 1.00f, 0.50f);
        style.setColor(ImGuiCol.ResizeGripHovered, 0.60f, 0.80f, 1.00f, 0.75f);
        style.setColor(ImGuiCol.ResizeGripActive, 0.70f, 0.90f, 1.00f, 1.00f);

        // Scrollbar
        style.setColor(ImGuiCol.ScrollbarBg, 0.10f, 0.10f, 0.12f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab, 0.30f, 0.30f, 0.35f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, 0.40f, 0.40f, 0.50f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabActive, 0.45f, 0.45f, 0.55f, 1.00f);

        // Style tweaks
        style.setWindowRounding(5.0f);
        style.setFrameRounding(5.0f);
        style.setGrabRounding(5.0f);
        style.setTabRounding(5.0f);
        style.setPopupRounding(5.0f);
        style.setScrollbarRounding(5.0f);
        style.setWindowPadding(10, 10);
        style.setFramePadding(6, 4);
        style.setItemSpacing(8, 6);
        style.setPopupBorderSize(0.0f);
    }

    public static void applyCatppuccinMocha() {
        ImGuiStyle style = ImGui.getStyle();

        ImGui.styleColorsDark();

        // Base colors inspired by Catppuccin Mocha
        style.setColor(ImGuiCol.Text, 0.90f, 0.89f, 0.88f, 1.00f);
        style.setColor(ImGuiCol.TextDisabled, 0.60f, 0.56f, 0.52f, 1.00f);
        style.setColor(ImGuiCol.WindowBg, 0.17f, 0.14f, 0.20f, 1.00f);
        style.setColor(ImGuiCol.ChildBg, 0.18f, 0.16f, 0.22f, 1.00f);
        style.setColor(ImGuiCol.PopupBg, 0.17f, 0.14f, 0.20f, 1.00f);
        style.setColor(ImGuiCol.Border, 0.27f, 0.23f, 0.29f, 1.00f);
        style.setColor(ImGuiCol.BorderShadow, 0.00f, 0.00f, 0.00f, 0.00f);
        style.setColor(ImGuiCol.FrameBg, 0.21f, 0.18f, 0.25f, 1.00f);
        style.setColor(ImGuiCol.FrameBgHovered, 0.24f, 0.20f, 0.29f, 1.00f);
        style.setColor(ImGuiCol.FrameBgActive, 0.26f, 0.22f, 0.31f, 1.00f);
        style.setColor(ImGuiCol.TitleBg, 0.14f, 0.12f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.TitleBgActive, 0.17f, 0.15f, 0.21f, 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed, 0.14f, 0.12f, 0.18f, 1.00f);
        style.setColor(ImGuiCol.MenuBarBg, 0.17f, 0.15f, 0.22f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarBg, 0.17f, 0.14f, 0.20f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab, 0.21f, 0.18f, 0.25f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, 0.24f, 0.20f, 0.29f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabActive, 0.26f, 0.22f, 0.31f, 1.00f);
        style.setColor(ImGuiCol.CheckMark, 0.95f, 0.66f, 0.47f, 1.00f);
        style.setColor(ImGuiCol.SliderGrab, 0.82f, 0.61f, 0.85f, 1.00f);
        style.setColor(ImGuiCol.SliderGrabActive, 0.89f, 0.54f, 0.79f, 1.00f);
        style.setColor(ImGuiCol.Button, 0.65f, 0.34f, 0.46f, 1.00f);
        style.setColor(ImGuiCol.ButtonHovered, 0.71f, 0.40f, 0.52f, 1.00f);
        style.setColor(ImGuiCol.ButtonActive, 0.76f, 0.46f, 0.58f, 1.00f);
        style.setColor(ImGuiCol.Header, 0.65f, 0.34f, 0.46f, 1.00f);
        style.setColor(ImGuiCol.HeaderHovered, 0.71f, 0.40f, 0.52f, 1.00f);
        style.setColor(ImGuiCol.HeaderActive, 0.76f, 0.46f, 0.58f, 1.00f);
        style.setColor(ImGuiCol.Separator, 0.27f, 0.23f, 0.29f, 1.00f);
        style.setColor(ImGuiCol.SeparatorHovered, 0.95f, 0.66f, 0.47f, 1.00f);
        style.setColor(ImGuiCol.SeparatorActive, 0.95f, 0.66f, 0.47f, 1.00f);
        style.setColor(ImGuiCol.ResizeGrip, 0.82f, 0.61f, 0.85f, 1.00f);
        style.setColor(ImGuiCol.ResizeGripHovered, 0.89f, 0.54f, 0.79f, 1.00f);
        style.setColor(ImGuiCol.ResizeGripActive, 0.92f, 0.61f, 0.85f, 1.00f);
        style.setColor(ImGuiCol.Tab, 0.21f, 0.18f, 0.25f, 1.00f);
        style.setColor(ImGuiCol.TabHovered, 0.82f, 0.61f, 0.85f, 1.00f);
        style.setColor(ImGuiCol.TabActive, 0.76f, 0.46f, 0.58f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocused, 0.18f, 0.16f, 0.22f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocusedActive, 0.21f, 0.18f, 0.25f, 1.00f);

        // Style adjustments
        style.setWindowRounding(6.0f);
        style.setFrameRounding(4.0f);
        style.setScrollbarRounding(4.0f);
        style.setGrabRounding(3.0f);
        style.setChildRounding(4.0f);
        style.setWindowPadding(8.0f, 8.0f);
        style.setFramePadding(5.0f, 4.0f);
        style.setItemSpacing(6.0f, 6.0f);
        style.setItemInnerSpacing(6.0f, 6.0f);
        style.setIndentSpacing(22.0f);
        style.setScrollbarSize(14.0f);
        style.setGrabMinSize(10.0f);
    }

    public static void applyModernDark() {
        ImGuiStyle style = ImGui.getStyle();

        ImGui.styleColorsDark();

        // Base color scheme
        style.setColor(ImGuiCol.Text, 0.92f, 0.92f, 0.92f, 1.00f);
        style.setColor(ImGuiCol.TextDisabled, 0.50f, 0.50f, 0.50f, 1.00f);
        style.setColor(ImGuiCol.WindowBg, 0.13f, 0.14f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.ChildBg, 0.13f, 0.14f, 0.15f, 1.00f);
        style.setColor(ImGuiCol.PopupBg, 0.10f, 0.10f, 0.11f, 0.94f);
        style.setColor(ImGuiCol.Border, 0.43f, 0.43f, 0.50f, 0.50f);
        style.setColor(ImGuiCol.BorderShadow, 0.00f, 0.00f, 0.00f, 0.00f);
        style.setColor(ImGuiCol.FrameBg, 0.20f, 0.21f, 0.22f, 1.00f);
        style.setColor(ImGuiCol.FrameBgHovered, 0.25f, 0.26f, 0.27f, 1.00f);
        style.setColor(ImGuiCol.FrameBgActive, 0.18f, 0.19f, 0.20f, 1.00f);
        style.setColor(ImGuiCol.TitleBg, 0.15f, 0.15f, 0.16f, 1.00f);
        style.setColor(ImGuiCol.TitleBgActive, 0.15f, 0.15f, 0.16f, 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed, 0.15f, 0.15f, 0.16f, 1.00f);
        style.setColor(ImGuiCol.MenuBarBg, 0.20f, 0.20f, 0.21f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarBg, 0.20f, 0.21f, 0.22f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrab, 0.28f, 0.28f, 0.29f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, 0.33f, 0.34f, 0.35f, 1.00f);
        style.setColor(ImGuiCol.ScrollbarGrabActive, 0.40f, 0.40f, 0.41f, 1.00f);
        style.setColor(ImGuiCol.CheckMark, 0.76f, 0.76f, 0.76f, 1.00f);
        style.setColor(ImGuiCol.SliderGrab, 0.28f, 0.56f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.SliderGrabActive, 0.37f, 0.61f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.Button, 0.20f, 0.25f, 0.30f, 1.00f);
        style.setColor(ImGuiCol.ButtonHovered, 0.30f, 0.35f, 0.40f, 1.00f);
        style.setColor(ImGuiCol.ButtonActive, 0.25f, 0.30f, 0.35f, 1.00f);
        style.setColor(ImGuiCol.Header, 0.25f, 0.25f, 0.25f, 0.80f);
        style.setColor(ImGuiCol.HeaderHovered, 0.30f, 0.30f, 0.30f, 0.80f);
        style.setColor(ImGuiCol.HeaderActive, 0.35f, 0.35f, 0.35f, 0.80f);
        style.setColor(ImGuiCol.Separator, 0.43f, 0.43f, 0.50f, 0.50f);
        style.setColor(ImGuiCol.SeparatorHovered, 0.33f, 0.67f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.SeparatorActive, 0.33f, 0.67f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.ResizeGrip, 0.28f, 0.56f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.ResizeGripHovered, 0.37f, 0.61f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.ResizeGripActive, 0.37f, 0.61f, 1.00f, 1.00f);
        style.setColor(ImGuiCol.Tab, 0.15f, 0.18f, 0.22f, 1.00f);
        style.setColor(ImGuiCol.TabHovered, 0.38f, 0.48f, 0.69f, 1.00f);
        style.setColor(ImGuiCol.TabActive, 0.28f, 0.38f, 0.59f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocused, 0.15f, 0.18f, 0.22f, 1.00f);
        style.setColor(ImGuiCol.TabUnfocusedActive, 0.15f, 0.18f, 0.22f, 1.00f);

        // Style adjustments
        style.setWindowRounding(5.3f);
        style.setFrameRounding(2.3f);
        style.setScrollbarRounding(0);
        style.setWindowPadding(8.0f, 8.0f);
        style.setFramePadding(5.0f, 5.0f);
        style.setItemSpacing(6.0f, 6.0f);
        style.setItemInnerSpacing(6.0f, 6.0f);
        style.setIndentSpacing(25.0f);
    }
}
