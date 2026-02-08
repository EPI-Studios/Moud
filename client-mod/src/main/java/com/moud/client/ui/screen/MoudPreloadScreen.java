package com.moud.client.ui.screen;

import com.moud.client.ui.loading.MoudPreloadState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class MoudPreloadScreen extends Screen {
    private final long openedAtMs = System.currentTimeMillis();
    private boolean closing = false;
    private long closingAtMs = 0L;

    public MoudPreloadScreen() {
        super(Text.of("Moud"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public void beginClose() {
        if (closing) return;
        closing = true;
        closingAtMs = System.currentTimeMillis();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float fadeIn = MathHelper.clamp((System.currentTimeMillis() - openedAtMs) / 250.0f, 0.0f, 1.0f);
        float fadeOut = closing ? (1.0f - MathHelper.clamp((System.currentTimeMillis() - closingAtMs) / 250.0f, 0.0f, 1.0f)) : 1.0f;
        float alphaF = fadeIn * fadeOut;
        int bgAlpha = (int) (alphaF * 255.0f);

        context.fill(0, 0, this.width, this.height, (bgAlpha << 24));

        if (closing && alphaF <= 0.01f) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen == this) {
                client.setScreen(null);
            }
            return;
        }

        int rightX = this.width - 40;
        int bottomY = this.height - 40;

        renderSpinner(context, rightX - 12, bottomY - 120, 12, 2.5f, alphaF);

        String title = "Loading";
        int titleAlpha = (int) (alphaF * 255.0f);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(rightX - this.textRenderer.getWidth(title), bottomY - 90, 0);
        context.drawTextWithShadow(this.textRenderer, title, 0, 0, (titleAlpha << 24) | 0xFFFFFF);
        matrices.pop();

        String phase = MoudPreloadState.phase();
        int dots = (int) ((System.currentTimeMillis() / 300L) % 4L);
        String suffix = ".".repeat(dots);
        String phaseText = phase + suffix;

        int phaseAlpha = (int) (alphaF * 170);
        int phaseColor = (phaseAlpha << 24) | 0xAAAAAA;
        context.drawText(this.textRenderer, phaseText,
                rightX - this.textRenderer.getWidth(phaseText),
                bottomY - 70, phaseColor, false);

        int percent = Math.round(MoudPreloadState.progress() * 100f);
        String percentText = percent + "%";
        context.drawText(this.textRenderer, percentText,
                rightX - this.textRenderer.getWidth(percentText),
                bottomY - 58, phaseColor, false);

        if (!MoudPreloadState.isActive() && !closing) {
            beginClose();
        }
    }

    private void renderSpinner(DrawContext context, int x, int y, int radius, float dotSize, float alpha) {
        int numDots = 8;
        float timeSeconds = System.currentTimeMillis() / 1000f;
        float dotsPerSecond = 9.0f;
        float phase = (timeSeconds * dotsPerSecond) % numDots;
        float rotation = timeSeconds * (float) (Math.PI * 2.0) * 1.2f;

        for (int i = 0; i < numDots; i++) {
            float rel = (i - phase) % numDots;
            if (rel < 0) rel += numDots;
            float opacity = 1.0f - (rel / numDots);
            opacity = MathHelper.clamp(opacity, 0.15f, 1.0f);
            opacity *= alpha;

            double angle = (2 * Math.PI * i / numDots) + rotation;
            float size = dotSize * (0.75f + 0.55f * opacity);

            int dotAlpha = (int) (opacity * 255.0f);
            int color = (dotAlpha << 24) | 0xFFFFFF;

            int dotX = x + (int) (Math.cos(angle) * radius);
            int dotY = y + (int) (Math.sin(angle) * radius);

            context.fill(
                    (int) (dotX - size), (int) (dotY - size),
                    (int) (dotX + size), (int) (dotY + size),
                    color
            );
        }
    }
}