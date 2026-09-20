package com.meekdev.moud.mod.client.editor.project;

import com.meekdev.moud.mod.client.editor.style.EditorFonts;
import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseCursor;

public final class HubStyle {

    public static final int BACKGROUND = color(0x181818);
    public static final int TOP_BAR = color(0x141414);
    public static final int SURFACE = color(0x1F1F1F);
    public static final int SURFACE_HOVER = color(0x262626);
    public static final int SURFACE_ACTIVE = color(0x303030);
    public static final int LINE = color(0x2A2A2A);
    public static final int LINE_STRONG = color(0x383838);
    public static final int TEXT_BRIGHT = color(0xFFFFFF);
    public static final int TEXT_MAIN = color(0xF0F0F0);
    public static final int TEXT = color(0xD0D0D0);
    public static final int TEXT_MUTED = color(0x9A9A9A);
    public static final int TEXT_LIGHT = color(0x6A6A6A);
    public static final int ACCENT = color(0xC6C6C6);
    public static final int ART = color(0x1C1C1F);
    public static final int ART_GLOW = color(0x2A2A2E);
    public static final int ART_MARK = color(0xD8D8DC);
    public static final int DIM = EditorStyle.rgba(0, 0, 0, 153);

    public static final float TITLE = 30.0f;
    public static final float HEADING = 24.0f;
    public static final float TILE_TITLE = 17.0f;
    public static final float BRAND = 20.0f;
    public static final float BODY = 15.0f;
    public static final float ITEM = 15.0f;
    public static final float SMALL = 14.0f;
    public static final float CAPTION = 12.0f;

    public static final float ACTION_HEIGHT = 36.0f;
    public static final float CAPTION_TRACKING = 0.07f;
    public static final float CODE_TRACKING = 0.26f;

    private static final float FRAME_ROUNDING = 6.0f;
    private static final float POPUP_ROUNDING = 8.0f;
    private static final float FRAME_PADDING_X = 12.0f;
    private static final float FRAME_PADDING_Y = 10.0f;
    private static final float ITEM_SPACING = 8.0f;
    private static final float WINDOW_PADDING = 8.0f;
    private static final int GLOW_STEPS = 22;

    private HubStyle() {}

    public static void apply() {
        EditorStyle.apply();
        ImGuiStyle style = ImGui.getStyle();
        style.setWindowRounding(0.0f);
        style.setChildRounding(0.0f);
        style.setFrameRounding(EditorScale.of(FRAME_ROUNDING));
        style.setPopupRounding(EditorScale.of(POPUP_ROUNDING));
        style.setScrollbarRounding(EditorScale.of(POPUP_ROUNDING));
        style.setWindowPadding(EditorScale.of(WINDOW_PADDING), EditorScale.of(WINDOW_PADDING));
        style.setFramePadding(EditorScale.of(FRAME_PADDING_X), EditorScale.of(FRAME_PADDING_Y));
        style.setItemSpacing(EditorScale.of(ITEM_SPACING), EditorScale.of(ITEM_SPACING));
        style.setPopupBorderSize(hairline());
        style.setFrameBorderSize(hairline());
        style.setColor(ImGuiCol.WindowBg, BACKGROUND);
        style.setColor(ImGuiCol.ChildBg, BACKGROUND);
        style.setColor(ImGuiCol.PopupBg, SURFACE);
        style.setColor(ImGuiCol.Border, LINE_STRONG);
        style.setColor(ImGuiCol.Text, TEXT_MAIN);
        style.setColor(ImGuiCol.TextDisabled, TEXT_LIGHT);
        style.setColor(ImGuiCol.TextSelectedBg, EditorStyle.withAlpha(ACCENT, 0.3f));
        style.setColor(ImGuiCol.FrameBg, BACKGROUND);
        style.setColor(ImGuiCol.FrameBgHovered, BACKGROUND);
        style.setColor(ImGuiCol.FrameBgActive, BACKGROUND);
        style.setColor(ImGuiCol.Button, SURFACE_HOVER);
        style.setColor(ImGuiCol.ButtonHovered, SURFACE_ACTIVE);
        style.setColor(ImGuiCol.ButtonActive, SURFACE_ACTIVE);
        style.setColor(ImGuiCol.Header, SURFACE_HOVER);
        style.setColor(ImGuiCol.HeaderHovered, SURFACE_HOVER);
        style.setColor(ImGuiCol.HeaderActive, SURFACE_ACTIVE);
        style.setColor(ImGuiCol.Separator, LINE);
        style.setColor(ImGuiCol.ScrollbarBg, EditorStyle.rgba(0, 0, 0, 0));
        style.setColor(ImGuiCol.ScrollbarGrab, SURFACE_ACTIVE);
        style.setColor(ImGuiCol.ScrollbarGrabHovered, LINE_STRONG);
        style.setColor(ImGuiCol.ScrollbarGrabActive, TEXT_LIGHT);
        style.setColor(ImGuiCol.ModalWindowDimBg, DIM);
    }

    public static void asEditor(Runnable body) {
        EditorStyle.apply();
        ImFont font = EditorFonts.body();
        if (font != null) ImGui.pushFont(font, EditorScale.of(EditorFonts.BODY));
        body.run();
        if (font != null) ImGui.popFont();
        apply();
    }

    public static float hairline() {
        return EditorScale.ofAtLeastOne(1.0f);
    }

    public static void write(float size, boolean bold, int color, String value) {
        ImFont font = EditorFonts.page(size, bold);
        if (font != null) ImGui.pushFont(font, EditorScale.of(EditorFonts.pageSize(size, bold)));
        ImGui.pushStyleColor(ImGuiCol.Text, color);
        ImGui.textUnformatted(value);
        ImGui.popStyleColor();
        if (font != null) ImGui.popFont();
    }

    public static void label(String text) {
        write(ITEM, true, TEXT_MUTED, text);
        ImGui.dummy(0.0f, EditorScale.of(4.0f));
    }

    public static void paint(ImDrawList draw, float size, boolean bold, float x, float y, int color, String value) {
        ImFont font = EditorFonts.page(size, bold);
        if (font == null) draw.addText(x, y, color, value);
        else draw.addText(font, EditorScale.ofInteger(EditorFonts.pageSize(size, bold)), x, y, color, value);
    }

    public static void paintTracked(ImDrawList draw, float size, float x, float y, int color, String value) {
        paintTracked(draw, size, x, y, color, value, CAPTION_TRACKING);
    }

    public static void paintTracked(ImDrawList draw, float size, float x, float y, int color, String value, float tracking) {
        float step = EditorScale.of(size) * tracking;
        float cursor = x;
        for (int index = 0; index < value.length(); index++) {
            String glyph = String.valueOf(value.charAt(index));
            paint(draw, size, true, cursor, y, color, glyph);
            cursor += widthOf(size, true, glyph) + step;
        }
    }

    public static float trackedWidth(float size, String value, float tracking) {
        if (value.isEmpty()) return 0.0f;
        float total = -EditorScale.of(size) * tracking;
        for (int index = 0; index < value.length(); index++) {
            total += widthOf(size, true, String.valueOf(value.charAt(index))) + EditorScale.of(size) * tracking;
        }
        return total;
    }

    public static float widthOf(float size, boolean bold, String value) {
        ImFont font = EditorFonts.page(size, bold);
        if (font == null) return ImGui.calcTextSizeX(value);
        return font.calcTextSizeAX(EditorScale.of(EditorFonts.pageSize(size, bold)), Float.MAX_VALUE, 0.0f, value);
    }

    public static float middle(float top, float height, float size) {
        return top + (height - EditorScale.of(size)) * 0.5f;
    }

    public static boolean action(String id, String label, float width, boolean primary, boolean enabled) {
        float height = EditorScale.of(ACTION_HEIGHT);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton("##" + id, width, height);
        float emphasis = enabled ? EditorMotion.towards(id, ImGui.isItemHovered()) : 0.0f;
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = EditorScale.of(FRAME_ROUNDING);
        int fill = primary
                ? EditorMotion.blend(TEXT_MAIN, TEXT_BRIGHT, emphasis)
                : EditorMotion.blend(SURFACE_HOVER, SURFACE_ACTIVE, emphasis);
        if (!enabled) fill = EditorStyle.withAlpha(fill, 0.35f);
        draw.addRectFilled(x, y, x + width, y + height, fill, rounding);
        if (!primary) draw.addRect(x, y, x + width, y + height, LINE_STRONG, rounding, 0, hairline());
        int ink = primary ? TOP_BAR : TEXT_MAIN;
        if (!enabled) ink = EditorStyle.withAlpha(ink, 0.6f);
        paint(draw, ITEM, true, x + (width - widthOf(ITEM, true, label)) * 0.5f, middle(y, height, ITEM), ink, label);
        if (enabled && ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        return clicked && enabled;
    }

    public static void glow(ImDrawList draw, float centerX, float centerY, float radiusX, float radiusY, int tint, float strength) {
        int layer = EditorStyle.withAlpha(tint, strength / GLOW_STEPS);
        for (int step = GLOW_STEPS; step > 0; step--) {
            float reach = step / (float) GLOW_STEPS;
            draw.addEllipseFilled(centerX, centerY, radiusX * reach, radiusY * reach, layer);
        }
    }

    private static int color(int rgb) {
        return EditorStyle.rgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }
}
