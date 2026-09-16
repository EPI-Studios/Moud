package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.mod.adapter.ui.UiImages;
import com.meekdev.moud.mod.place.PlaceToml;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.impl.ImGuiMCImpl;
import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jspecify.annotations.Nullable;

final class ScreenPreviews {

    enum Shown { NONE, LOADING, PAUSE, DISCONNECTED }

    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xAAFFFFFF;
    private static final int VEIL = 0x88000000;
    private static final int BUTTON = 0xFF6B6B6B;
    private static final int BUTTON_EDGE = 0xFF1E1E1E;
    private static final long TIP_MILLIS = 5000;
    private static final long DOT_MILLIS = 400;

    private Shown shown = Shown.NONE;

    Shown shown() {
        return shown;
    }

    void show(Shown value) {
        shown = value;
    }

    void draw(ImDrawList draw, float left, float top, float right, float bottom) {
        if (shown == Shown.NONE) return;
        float width = right - left;
        float height = bottom - top;
        float unit = height / 240f;
        switch (shown) {
            case LOADING -> loading(draw, left, top, width, height, unit, true);
            case PAUSE -> {
                draw.addRectFilled(left, top, right, bottom, VEIL);
                text(draw, "Paused", left + width / 2, top + height / 2 - 52 * unit, unit * 1.2f, WHITE);
                button(draw, "Resume", left + width / 2, top + height / 2 - 20 * unit, unit);
                button(draw, "Settings", left + width / 2, top + height / 2 + 4 * unit, unit);
                button(draw, "Quit", left + width / 2, top + height / 2 + 28 * unit, unit);
            }
            case DISCONNECTED -> {
                loading(draw, left, top, width, height, unit, false);
                draw.addRectFilled(left, top, right, bottom, VEIL);
                text(draw, "Disconnected", left + width / 2, top + height / 2 - 40 * unit, unit * 1.2f, WHITE);
                text(draw, "The reason a script passed to player:kick", left + width / 2, top + height / 2 - 20 * unit, unit, 0xFFCCCCCC);
                button(draw, "Try again", left + width / 2 - 64 * unit, top + height / 2 + 30 * unit, unit);
                button(draw, "Quit", left + width / 2 + 64 * unit, top + height / 2 + 30 * unit, unit);
            }
            default -> { }
        }
    }

    private static void loading(ImDrawList draw, float left, float top, float width, float height, float unit, boolean words) {
        PlaceConfig config = PlaceToml.config();
        PlaceConfig.Loading loading = config.loading();
        int rgb = Integer.parseInt(loading.color().substring(1), 16);
        draw.addRectFilled(left, top, left + width, top + height, 0xFF000000 | abgr(rgb));
        Image background = image(loading.background());
        if (background != null) {
            float scale = Math.max(width / background.width, height / background.height);
            float w = background.width * scale;
            float h = background.height * scale;
            float x = left + (width - w) / 2;
            float y = top + (height - h) / 2;
            draw.pushClipRect(left, top, left + width, top + height, true);
            draw.addImage(background.id, x, y, x + w, y + h);
            draw.popClipRect();
        }
        if (!words) return;
        float middle = top + height * 0.42f;
        Image logo = image(loading.logo());
        if (logo != null) {
            float scale = Math.min(width * 0.6f / logo.width, height * 0.35f / logo.height);
            float w = logo.width * scale;
            float h = logo.height * scale;
            draw.addImage(logo.id, left + (width - w) / 2, middle - h / 2, left + (width + w) / 2, middle + h / 2);
        } else {
            text(draw, config.name(), left + width / 2, middle, unit * 2.5f, WHITE);
        }
        int dots = (int) (System.currentTimeMillis() / DOT_MILLIS % 4);
        text(draw, loading.text() + ".".repeat(dots), left + width / 2, top + height * 0.68f, unit, WHITE);
        if (!loading.tips().isEmpty()) {
            String tip = loading.tips().get((int) (System.currentTimeMillis() / TIP_MILLIS % loading.tips().size()));
            text(draw, tip, left + width / 2, top + height - 24 * unit, unit, MUTED);
        }
    }

    private static void button(ImDrawList draw, String label, float centreX, float centreY, float unit) {
        float w = 80 * unit;
        float h = 10 * unit;
        draw.addRectFilled(centreX - w, centreY - h, centreX + w, centreY + h, BUTTON);
        draw.addRect(centreX - w, centreY - h, centreX + w, centreY + h, BUTTON_EDGE);
        text(draw, label, centreX, centreY, unit, WHITE);
    }

    private static void text(ImDrawList draw, String text, float centreX, float centreY, float unit, int colour) {
        int size = Math.max(8, Math.round(9f * unit));
        float w = ImGui.calcTextSizeX(text) * size / ImGui.getFontSize();
        draw.addText(ImGui.getFont(), size, centreX - w / 2, centreY - size / 2f, abgrColour(colour), text);
    }

    private record Image(long id, float width, float height) {}

    private static @Nullable Image image(String res) {
        if (res.isEmpty()) return null;
        try {
            UiImages.Image image = UiImages.image(res);
            if (image == null) return null;
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(image.id());
            long id = ImGuiMCImpl.handler.getRenderer().getImGuiId(ImGuiMC.getTexture(texture), null);
            return new Image(id, image.width(), image.height());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int abgr(int rgb) {
        return (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 0xFF;
    }

    private static int abgrColour(int argb) {
        return (argb & 0xFF000000) | abgr(argb & 0xFFFFFF);
    }
}
