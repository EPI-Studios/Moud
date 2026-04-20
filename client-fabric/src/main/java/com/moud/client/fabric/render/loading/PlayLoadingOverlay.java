package com.moud.client.fabric.render.loading;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class PlayLoadingOverlay {

    private static final Identifier PATTERN = Identifier.of("moud", "textures/gui/loading_pattern.png");
    private static final int TILE = 64;
    private static final float SCROLL_PX_PER_SEC = 28f;

    private static final int BG_ARGB = 0xFF16181C;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int TITLE_SHADOW = 0xFF000000;
    private static final int STATUS_COLOR = 0xFFFFFFFF;

    private static final long EPOCH = System.currentTimeMillis();
    private static final float TITLE_SCALE_MIN = 1.0f;
    private static final float TITLE_SCALE_MAX = 3.0f;
    private static final float STATUS_SCALE_RATIO = 0.5f;

    private PlayLoadingOverlay() { }

    public static void render(DrawContext ctx) {
        if (!PlayLoading.isActive()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        ctx.fill(0, 0, w, h, BG_ARGB);

        float seconds = (System.currentTimeMillis() - EPOCH) / 1000f;
        float scroll = seconds * SCROLL_PX_PER_SEC;
        float offX = -(scroll % TILE);
        float offY = -(scroll % TILE);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(offX, offY, 0f);
        int tilesX = (w / TILE) + 3;
        int tilesY = (h / TILE) + 3;
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                ctx.drawTexture(PATTERN, tx * TILE, ty * TILE, TILE, TILE, 0f, 0f, TILE, TILE, TILE, TILE);
            }
        }
        ctx.getMatrices().pop();

        TextRenderer tr = mc.textRenderer;
        if (tr != null) {
            String title = PlayLoading.gameName();
            if (title.isBlank()) title = "Moud";
            int margin = Math.max(16, Math.min(w, h) / 20);
            int availableWidth = Math.max(120, w - margin * 2);
            float scale = computeTitleScale(tr, title, w, h, availableWidth);
            float statusScale = Math.max(1.0f, scale * STATUS_SCALE_RATIO);

            int titleW = (int) (tr.getWidth(title) * scale);
            int titleH = (int) (9 * scale);
            int lineGap = Math.max(8, Math.round(scale * 2.2f));

            int statusDots = ((int) (seconds * 2.5f) % 4);
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < statusDots; i++) dots.append('.');
            String statusLine = PlayLoading.currentSummary() + dots;
            int statusW = (int) (tr.getWidth(statusLine) * statusScale);
            int statusH = (int) (9 * statusScale);

            int blockH = titleH + lineGap + statusH;
            int blockRight = w - margin;
            int blockBottom = h - margin;

            int titleX = blockRight - titleW;
            int titleY = blockBottom - blockH;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(titleX, titleY, 0f);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.drawText(tr, title, 1, 1, TITLE_SHADOW, false);
            ctx.drawText(tr, title, 0, 0, TITLE_COLOR, false);
            ctx.getMatrices().pop();

            int statusX = blockRight - statusW;
            int statusY = blockBottom - statusH;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(statusX, statusY, 0f);
            ctx.getMatrices().scale(statusScale, statusScale, 1f);
            ctx.drawText(tr, statusLine, 0, 0, STATUS_COLOR, false);
            ctx.getMatrices().pop();
        }
    }

    private static float computeTitleScale(TextRenderer tr, String title, int width, int height, int availableWidth) {
        float viewportScale = height / 240f;
        float fitScale = availableWidth / (float) Math.max(1, tr.getWidth(title));
        return clamp(Math.min(viewportScale, fitScale), TITLE_SCALE_MIN, TITLE_SCALE_MAX);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
