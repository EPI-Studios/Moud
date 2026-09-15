package com.meekdev.moud.mod.client.editor.style;

import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiDir;

import java.util.Optional;

public final class EditorStyle {

    private static final int BASE_SURFACE = 41;

    public static final int COLOR_WINDOW_BACKGROUND = surface(0.49f);
    public static final int COLOR_PANEL_BACKGROUND = surface(1.0f);
    public static final int COLOR_HEADER_BACKGROUND = surface(0.49f);
    public static final int COLOR_SUNKEN_BACKGROUND = surface(0.43f);
    public static final int COLOR_FIELD_BACKGROUND = surface(0.67f);
    public static final int COLOR_FIELD_HOVER = surface(0.76f);
    public static final int COLOR_FIELD_ACTIVE = surface(0.86f);
    public static final int COLOR_WIDGET_BACKGROUND = surface(1.6f);
    public static final int COLOR_WIDGET_HOVER = surface(1.87f);
    public static final int COLOR_WIDGET_ACTIVE = surface(1.96f);
    public static final int COLOR_WIDGET_OUTLINE = surface(1.75f);
    public static final int COLOR_ELEVATED_BACKGROUND = surface(1.39f);
    public static final int COLOR_OUTLINE = surface(0.76f);
    public static final int COLOR_TEXT = rgba(255, 255, 255, 191);
    public static final int COLOR_TEXT_MUTED = rgba(255, 255, 255, 140);
    public static final int COLOR_TEXT_FAINT = rgba(255, 255, 255, 89);
    public static final int COLOR_TEXT_FOCUS = rgb(255, 255, 255);
    public static final int COLOR_TEXT_ON_ACCENT = rgb(16, 16, 16);
    public static final int COLOR_ACCENT = rgb(198, 198, 198);
    public static final int COLOR_ACCENT_HOVER = rgb(226, 226, 226);
    public static final int COLOR_HIGHLIGHT = rgb(255, 138, 31);
    public static final int COLOR_DANGER = rgb(224, 92, 86);
    public static final int COLOR_WARNING = rgb(218, 178, 76);
    public static final int COLOR_SUCCESS = rgb(124, 188, 108);
    public static final int COLOR_SYSTEM = rgb(158, 158, 158);
    public static final int COLOR_AXIS_X = rgb(214, 90, 86);
    public static final int COLOR_AXIS_Y = rgb(126, 186, 102);
    public static final int COLOR_AXIS_Z = rgb(96, 140, 206);

    private static final float WINDOW_ROUNDING = 0.0f;
    private static final float CHILD_ROUNDING = 4.0f;
    private static final float FRAME_ROUNDING = 4.0f;
    private static final float POPUP_ROUNDING = 4.0f;
    private static final float SCROLLBAR_ROUNDING = 4.0f;
    private static final float GRAB_ROUNDING = 4.0f;
    private static final float TAB_ROUNDING = 4.0f;
    private static final float TAB_CLOSE_BUTTON_ON_HOVER = 0.0f;
    private static final float TAB_OVERLINE_SIZE = 2.0f;
    private static final float WINDOW_PADDING = 6.0f;
    private static final float FRAME_PADDING_X = 6.0f;
    private static final float FRAME_PADDING_Y = 5.0f;
    private static final float ITEM_SPACING_X = 4.0f;
    private static final float ITEM_SPACING_Y = 4.0f;
    private static final float INNER_SPACING = 4.0f;
    private static final float CELL_PADDING_X = 6.0f;
    private static final float CELL_PADDING_Y = 4.0f;
    private static final float INDENT_SPACING = 14.0f;
    private static final float SCROLLBAR_SIZE = 10.0f;
    private static final float GRAB_MINIMUM_SIZE = 10.0f;
    private static final float BORDER_SIZE = 1.0f;
    private static final float ICON_SIZE_SMALL = 14.0f;
    private static final float ICON_SIZE_MEDIUM = 16.0f;
    private static final float ICON_SIZE_TOOLBAR = 15.0f;
    private static final float FONT_PIXEL_HEIGHT = 14.0f;
    private static final float TITLE_FONT_PIXEL_HEIGHT = 15.0f;
    private static final float SMALL_FONT_PIXEL_HEIGHT = 13.0f;
    private static final float MONOSPACE_FONT_PIXEL_HEIGHT = 14.0f;

    private static ImFont monospaceFont;
    private static ImFont titleFont;
    private static ImFont smallFont;

    private EditorStyle() {
    }

    public static float windowRounding() {
        return EditorScale.of(WINDOW_ROUNDING);
    }

    public static float frameRounding() {
        return EditorScale.of(FRAME_ROUNDING);
    }

    public static float windowPadding() {
        return EditorScale.of(WINDOW_PADDING);
    }

    public static float framePaddingX() {
        return EditorScale.of(FRAME_PADDING_X);
    }

    public static float framePaddingY() {
        return EditorScale.of(FRAME_PADDING_Y);
    }

    public static float itemSpacingX() {
        return EditorScale.of(ITEM_SPACING_X);
    }

    public static float itemSpacingY() {
        return EditorScale.of(ITEM_SPACING_Y);
    }

    public static float innerSpacing() {
        return EditorScale.of(INNER_SPACING);
    }

    public static float indentSpacing() {
        return EditorScale.of(INDENT_SPACING);
    }

    public static float scrollbarSize() {
        return EditorScale.of(SCROLLBAR_SIZE);
    }

    public static float borderSize() {
        return EditorScale.ofAtLeastOne(BORDER_SIZE);
    }

    public static float iconSizeSmall() {
        return EditorScale.of(ICON_SIZE_SMALL);
    }

    public static float iconSizeMedium() {
        return EditorScale.of(ICON_SIZE_MEDIUM);
    }

    public static float iconSizeToolbar() {
        return EditorScale.of(ICON_SIZE_TOOLBAR);
    }

    public static float fontPixelHeight() {
        return EditorScale.of(FONT_PIXEL_HEIGHT);
    }

    public static float monospaceFontPixelHeight() {
        return EditorScale.of(MONOSPACE_FONT_PIXEL_HEIGHT);
    }

    public static float titleFontPixelHeight() {
        return EditorScale.of(TITLE_FONT_PIXEL_HEIGHT);
    }

    public static float smallFontPixelHeight() {
        return EditorScale.of(SMALL_FONT_PIXEL_HEIGHT);
    }

    public static void setMonospaceFont(ImFont font) {
        monospaceFont = font;
    }

    public static Optional<ImFont> monospaceFont() {
        return Optional.ofNullable(monospaceFont);
    }

    public static void setTitleFont(ImFont font) {
        titleFont = font;
    }

    public static void setSmallFont(ImFont font) {
        smallFont = font;
    }

    public static Optional<ImFont> titleFont() {
        return Optional.ofNullable(titleFont);
    }

    public static Optional<ImFont> smallFont() {
        return Optional.ofNullable(smallFont);
    }

    public static void apply() {
        ImGuiStyle style = ImGui.getStyle();
        applyRounding(style);
        applySpacing(style);
        applyBorders(style);
        applySurfaceColors(style);
        applyFieldColors(style);
        applyInteractiveColors(style);
        applyScrollbarColors(style);
        applyDockingColors(style);
        applyTableColors(style);
        applyPlotColors(style);
    }

    private static void applyRounding(ImGuiStyle style) {
        style.setWindowRounding(windowRounding());
        style.setChildRounding(EditorScale.of(CHILD_ROUNDING));
        style.setFrameRounding(frameRounding());
        style.setPopupRounding(EditorScale.of(POPUP_ROUNDING));
        style.setScrollbarRounding(EditorScale.of(SCROLLBAR_ROUNDING));
        style.setGrabRounding(EditorScale.of(GRAB_ROUNDING));
        style.setTabRounding(EditorScale.of(TAB_ROUNDING));
        style.setTabCloseButtonMinWidthSelected(TAB_CLOSE_BUTTON_ON_HOVER);
        style.setTabCloseButtonMinWidthUnselected(TAB_CLOSE_BUTTON_ON_HOVER);
    }

    private static void applySpacing(ImGuiStyle style) {
        style.setWindowPadding(windowPadding(), windowPadding());
        style.setFramePadding(framePaddingX(), framePaddingY());
        style.setItemSpacing(itemSpacingX(), itemSpacingY());
        style.setItemInnerSpacing(innerSpacing(), innerSpacing());
        style.setCellPadding(EditorScale.of(CELL_PADDING_X), EditorScale.of(CELL_PADDING_Y));
        style.setIndentSpacing(indentSpacing());
        style.setScrollbarSize(scrollbarSize());
        style.setGrabMinSize(EditorScale.of(GRAB_MINIMUM_SIZE));
        style.setWindowTitleAlign(0.0f, 0.5f);
        style.setButtonTextAlign(0.5f, 0.5f);
        style.setSelectableTextAlign(0.0f, 0.5f);
        style.setWindowMenuButtonPosition(ImGuiDir.None);
    }

    private static void applyBorders(ImGuiStyle style) {
        style.setWindowBorderSize(0.0f);
        style.setChildBorderSize(0.0f);
        style.setFrameBorderSize(0.0f);
        style.setPopupBorderSize(0.0f);
        style.setTabBorderSize(0.0f);
    }

    private static void applySurfaceColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.WindowBg, COLOR_WINDOW_BACKGROUND);
        style.setColor(ImGuiCol.ChildBg, COLOR_WINDOW_BACKGROUND);
        style.setColor(ImGuiCol.PopupBg, COLOR_PANEL_BACKGROUND);
        style.setColor(ImGuiCol.MenuBarBg, COLOR_HEADER_BACKGROUND);
        style.setColor(ImGuiCol.TitleBg, COLOR_HEADER_BACKGROUND);
        style.setColor(ImGuiCol.TitleBgActive, COLOR_PANEL_BACKGROUND);
        style.setColor(ImGuiCol.TitleBgCollapsed, COLOR_HEADER_BACKGROUND);
        style.setColor(ImGuiCol.Border, COLOR_OUTLINE);
        style.setColor(ImGuiCol.BorderShadow, rgba(0, 0, 0, 0));
        style.setColor(ImGuiCol.Text, COLOR_TEXT);
        style.setColor(ImGuiCol.TextDisabled, COLOR_TEXT_MUTED);
        style.setColor(ImGuiCol.TextSelectedBg, withAlpha(COLOR_ACCENT, 0.35f));
        style.setColor(ImGuiCol.ModalWindowDimBg, rgba(8, 8, 12, 150));
    }

    private static void applyFieldColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.FrameBg, COLOR_FIELD_BACKGROUND);
        style.setColor(ImGuiCol.FrameBgHovered, COLOR_FIELD_HOVER);
        style.setColor(ImGuiCol.FrameBgActive, COLOR_FIELD_ACTIVE);
        style.setColor(ImGuiCol.CheckMark, COLOR_ACCENT);
        style.setColor(ImGuiCol.SliderGrab, COLOR_ACCENT);
        style.setColor(ImGuiCol.SliderGrabActive, COLOR_ACCENT_HOVER);
    }

    private static void applyInteractiveColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.Button, COLOR_WIDGET_BACKGROUND);
        style.setColor(ImGuiCol.ButtonHovered, COLOR_WIDGET_HOVER);
        style.setColor(ImGuiCol.ButtonActive, COLOR_WIDGET_ACTIVE);
        style.setColor(ImGuiCol.Header, COLOR_PANEL_BACKGROUND);
        style.setColor(ImGuiCol.HeaderHovered, COLOR_WIDGET_HOVER);
        style.setColor(ImGuiCol.HeaderActive, COLOR_WIDGET_ACTIVE);
        style.setColor(ImGuiCol.Separator, COLOR_OUTLINE);
        style.setColor(ImGuiCol.SeparatorHovered, COLOR_ACCENT);
        style.setColor(ImGuiCol.SeparatorActive, COLOR_ACCENT_HOVER);
        style.setColor(ImGuiCol.ResizeGrip, withAlpha(COLOR_ACCENT, 0.35f));
        style.setColor(ImGuiCol.ResizeGripHovered, withAlpha(COLOR_ACCENT, 0.75f));
        style.setColor(ImGuiCol.ResizeGripActive, COLOR_ACCENT_HOVER);
        style.setColor(ImGuiCol.NavHighlight, COLOR_ACCENT);
        style.setColor(ImGuiCol.NavWindowingHighlight, withAlpha(COLOR_TEXT, 0.7f));
        style.setColor(ImGuiCol.NavWindowingDimBg, rgba(8, 8, 12, 100));
        style.setColor(ImGuiCol.DragDropTarget, COLOR_HIGHLIGHT);
    }

    private static void applyScrollbarColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.ScrollbarBg, COLOR_SUNKEN_BACKGROUND);
        style.setColor(ImGuiCol.ScrollbarGrab, rgb(56, 56, 71));
        style.setColor(ImGuiCol.ScrollbarGrabHovered, rgb(74, 80, 99));
        style.setColor(ImGuiCol.ScrollbarGrabActive, rgb(92, 101, 128));
    }

    private static void applyDockingColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.Tab, COLOR_HEADER_BACKGROUND);
        style.setColor(ImGuiCol.TabHovered, COLOR_ELEVATED_BACKGROUND);
        style.setColor(ImGuiCol.UnsavedMarker, COLOR_WARNING);
        style.setColor(ImGuiCol.TabSelectedOverline, rgba(0, 0, 0, 0));
        style.setColor(ImGuiCol.TabDimmedSelectedOverline, rgba(0, 0, 0, 0));
        style.setColor(ImGuiCol.TabActive, COLOR_PANEL_BACKGROUND);
        style.setColor(ImGuiCol.TabUnfocused, COLOR_HEADER_BACKGROUND);
        style.setColor(ImGuiCol.TabUnfocusedActive, COLOR_PANEL_BACKGROUND);
        style.setColor(ImGuiCol.DockingPreview, withAlpha(COLOR_ACCENT, 0.55f));
        style.setColor(ImGuiCol.DockingEmptyBg, COLOR_SUNKEN_BACKGROUND);
    }

    private static void applyTableColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.TableHeaderBg, COLOR_PANEL_BACKGROUND);
        style.setColor(ImGuiCol.TableBorderStrong, rgb(63, 63, 79));
        style.setColor(ImGuiCol.TableBorderLight, rgb(43, 43, 54));
        style.setColor(ImGuiCol.TableRowBg, rgba(0, 0, 0, 0));
        style.setColor(ImGuiCol.TableRowBgAlt, rgba(255, 255, 255, 10));
    }

    private static void applyPlotColors(ImGuiStyle style) {
        style.setColor(ImGuiCol.PlotLines, COLOR_ACCENT);
        style.setColor(ImGuiCol.PlotLinesHovered, COLOR_ACCENT_HOVER);
        style.setColor(ImGuiCol.PlotHistogram, COLOR_HIGHLIGHT);
        style.setColor(ImGuiCol.PlotHistogramHovered, rgb(255, 168, 77));
    }

    public static int surface(float elevation) {
        int value = Math.round(Math.clamp(BASE_SURFACE * elevation, 0.0f, 255.0f));
        return rgb(value, value, value);
    }

    public static int rgb(int red, int green, int blue) {
        return rgba(red, green, blue, 255);
    }

    public static int rgba(int red, int green, int blue, int alpha) {
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    public static int lighten(int abgrColor, float amount) {
        return mix(abgrColor, rgba(255, 255, 255, alphaOf(abgrColor)), amount);
    }

    public static int darken(int abgrColor, float amount) {
        return mix(abgrColor, rgba(0, 0, 0, alphaOf(abgrColor)), amount);
    }

    public static int mix(int fromColor, int toColor, float amount) {
        float clamped = Math.clamp(amount, 0.0f, 1.0f);
        return rgba(
                channel(fromColor, toColor, 0, clamped),
                channel(fromColor, toColor, 8, clamped),
                channel(fromColor, toColor, 16, clamped),
                channel(fromColor, toColor, 24, clamped));
    }

    public static int alphaOf(int abgrColor) {
        return (abgrColor >>> 24) & 0xFF;
    }

    private static int channel(int fromColor, int toColor, int shift, float amount) {
        int start = (fromColor >> shift) & 0xFF;
        int end = (toColor >> shift) & 0xFF;
        return Math.round(start + (end - start) * amount);
    }

    public static int withAlpha(int abgrColor, float alpha) {
        int clamped = Math.round(Math.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        return (abgrColor & 0x00FFFFFF) | (clamped << 24);
    }
}
