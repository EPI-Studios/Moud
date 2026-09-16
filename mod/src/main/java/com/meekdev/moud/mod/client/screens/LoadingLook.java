package com.meekdev.moud.mod.client.screens;

import com.meekdev.moud.core.place.PlaceConfig;
import com.meekdev.moud.mod.adapter.ui.UiImages;
import com.meekdev.moud.mod.place.Game;
import com.meekdev.moud.mod.place.PlaceToml;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class LoadingLook {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xAAFFFFFF;
    private static final long TIP_MILLIS = 5000;
    private static final long DOT_MILLIS = 400;

    private LoadingLook() {}

    public static boolean active() {
        if (Game.standalone()) return true;
        return PlaceToml.opened() && !PlaceToml.config().loading().equals(PlaceConfig.Loading.DEFAULT);
    }

    public static void draw(GuiGraphicsExtractor g, int width, int height) {
        PlaceConfig config = PlaceToml.config();
        PlaceConfig.Loading loading = config.loading();
        g.fill(0, 0, width, height, 0xFF000000 | Integer.parseInt(loading.color().substring(1), 16));
        UiImages.Image background = image(loading.background());
        if (background != null) {
            double scale = Math.max(width / (double) background.width(), height / (double) background.height());
            int w = (int) Math.ceil(background.width() * scale);
            int h = (int) Math.ceil(background.height() * scale);
            int x = (width - w) / 2;
            int y = (height - h) / 2;
            g.blit(background.id(), x, y, x + w, y + h, 0, 1, 0, 1);
        }
        Font font = Minecraft.getInstance().font;
        UiImages.Image logo = image(loading.logo());
        int middle = (int) (height * 0.42);
        if (logo != null) {
            double scale = Math.min(width * 0.6 / logo.width(), height * 0.35 / logo.height());
            int w = (int) (logo.width() * scale);
            int h = (int) (logo.height() * scale);
            g.blit(logo.id(), (width - w) / 2, middle - h / 2, (width + w) / 2, middle + h / 2, 0, 1, 0, 1);
        } else {
            g.pose().pushMatrix();
            g.pose().translate(width / 2f, middle - 8);
            g.pose().scale(2.5f, 2.5f);
            g.centeredText(font, config.name(), 0, 0, WHITE);
            g.pose().popMatrix();
        }
        int dots = (int) (System.currentTimeMillis() / DOT_MILLIS % 4);
        int textLeft = width / 2 - font.width(loading.text()) / 2;
        g.text(font, loading.text() + ".".repeat(dots), textLeft, (int) (height * 0.68), WHITE);
        if (!loading.tips().isEmpty()) {
            String tip = loading.tips().get((int) (System.currentTimeMillis() / TIP_MILLIS % loading.tips().size()));
            g.centeredText(font, tip, width / 2, height - 28, MUTED);
        }
    }

    private static UiImages.Image image(String res) {
        try {
            return res.isEmpty() ? null : UiImages.image(res);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
