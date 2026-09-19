package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import java.util.Locale;

final class Paint {

    static final int KEY = EditorStyle.rgb(214, 214, 214);
    static final int KEY_MUTED = EditorStyle.rgb(132, 132, 132);
    static final int SELECTED = EditorStyle.COLOR_HIGHLIGHT;
    static final int EVENT = EditorStyle.COLOR_WARNING;
    static final int LANE = EditorStyle.surface(0.62f);
    static final int LANE_ALT = EditorStyle.surface(0.58f);
    static final int GRID = EditorStyle.withAlpha(EditorStyle.rgb(255, 255, 255), 0.05f);
    static final int GRID_STRONG = EditorStyle.withAlpha(EditorStyle.rgb(255, 255, 255), 0.1f);

    private Paint() {}

    static void diamond(ImDrawList draw, float x, float y, float radius, int fill) {
        draw.addQuadFilled(x, y - radius, x + radius, y, x, y + radius, x - radius, y, fill);
    }

    static void diamondOutline(ImDrawList draw, float x, float y, float radius, int color, float thickness) {
        draw.addQuad(x, y - radius, x + radius, y, x, y + radius, x - radius, y, color, thickness);
    }

    static ImFont mono() {
        return EditorStyle.monospaceFont().orElse(ImGui.getFont());
    }

    static float monoSize() {
        return EditorStyle.monospaceFontPixelHeight();
    }

    static ImFont small() {
        return EditorStyle.smallFont().orElse(ImGui.getFont());
    }

    static float smallSize() {
        return EditorStyle.smallFontPixelHeight();
    }

    static float monoWidth(String text) {
        return mono().calcTextSizeA(monoSize(), Float.MAX_VALUE, 0, text).x;
    }

    static float smallWidth(String text) {
        return small().calcTextSizeA(smallSize(), Float.MAX_VALUE, 0, text).x;
    }

    static void mono(ImDrawList draw, float x, float y, int color, String text) {
        draw.addText(mono(), Math.round(monoSize()), x, y, color, text);
    }

    static void small(ImDrawList draw, float x, float y, int color, String text) {
        draw.addText(small(), Math.round(smallSize()), x, y, color, text);
    }

    static void pushMono() {
        EditorStyle.monospaceFont().ifPresent(font -> ImGui.pushFont(font, EditorStyle.monospaceFontPixelHeight()));
    }

    static void popMono() {
        EditorStyle.monospaceFont().ifPresent(font -> ImGui.popFont());
    }

    static void pushSmall() {
        EditorStyle.smallFont().ifPresent(font -> ImGui.pushFont(font, EditorStyle.smallFontPixelHeight()));
    }

    static void popSmall() {
        EditorStyle.smallFont().ifPresent(font -> ImGui.popFont());
    }

    static String seconds(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static String number(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.equals("-0.0") ? "0.0" : text;
    }

    static float px(float design) {
        return EditorScale.of(design);
    }

    static int axis(int index) {
        return switch (index) {
            case 0 -> EditorStyle.COLOR_AXIS_X;
            case 1 -> EditorStyle.COLOR_AXIS_Y;
            default -> EditorStyle.COLOR_AXIS_Z;
        };
    }

    static int channel(Channel channel) {
        return switch (channel) {
            case ROTATION -> EditorStyle.COLOR_AXIS_X;
            case POSITION -> EditorStyle.COLOR_AXIS_Y;
            case SCALE -> EditorStyle.COLOR_AXIS_Z;
        };
    }

    static void chip(ImDrawList draw, float x, float y, float height, String text, int color, boolean solid) {
        float pad = px(6);
        float width = monoWidth(text) + pad * 2;
        draw.addRectFilled(x, y, x + width, y + height, solid ? color : EditorStyle.withAlpha(color, 0.2f), px(3));
        mono(draw, x + pad, y + (height - monoSize()) * 0.5f, solid ? EditorStyle.COLOR_TEXT_ON_ACCENT : color, text);
    }

    static float chipWidth(String text) {
        return monoWidth(text) + px(6) * 2;
    }
}
