package com.moud.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UIRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIRenderer.class);
    private final MinecraftClient client = MinecraftClient.getInstance();

    public void render(DrawContext context, UIElement element, int mouseX, int mouseY, float tickDelta) {
        int x = element.getX();
        int y = element.getY();
        int width = element.getWidth();
        int height = element.getHeight();

        renderBackground(context, element, x, y, width, height);
        renderBorder(context, element, x, y, width, height);
        renderText(context, element, x, y, width, height);

        if (element.contains(mouseX, mouseY)) {
            element.triggerHover(mouseX, mouseY);
        }
    }

    private void renderBackground(DrawContext context, UIElement element, int x, int y, int width, int height) {
        int bgColor = parseColor(element.getBackgroundColor(), element.getOpacity());
        if ((bgColor >>> 24) > 0) {
            context.fill(x, y, x + width, y + height, bgColor);
        }
    }

    private void renderBorder(DrawContext context, UIElement element, int x, int y, int width, int height) {
        int borderWidth = element.getBorderWidth();
        if (borderWidth > 0) {
            int borderColor = parseColor(element.getBorderColor(), element.getOpacity());
            context.drawBorder(x, y, width, height, borderColor);
        }
    }

    private void renderText(DrawContext context, UIElement element, int x, int y, int width, int height) {
        String displayText = getDisplayText(element);
        if (displayText.isEmpty()) return;

        int textColor = parseColor(element.getTextColor(), element.getOpacity());
        int textWidth = client.textRenderer.getWidth(displayText);
        int textHeight = client.textRenderer.fontHeight;

        int textX = x + 4;
        int textY = y + (height - textHeight) / 2;

        if ("button".equals(element.getType())) {
            textX = x + (width - textWidth) / 2;
        }

        context.drawText(client.textRenderer, Text.literal(displayText), textX, textY, textColor, true);

        if ("input".equals(element.getType()) && !element.getValue().isEmpty()) {
            renderInputCursor(context, element, textX, textY, textHeight);
        }
    }

    private String getDisplayText(UIElement element) {
        switch (element.getType()) {
            case "input":
                String value = element.getValue();
                if (!value.isEmpty()) {
                    return value;
                }
                return element.getPlaceholder();
            default:
                return element.getText();
        }
    }

    private void renderInputCursor(DrawContext context, UIElement element, int textX, int textY, int textHeight) {
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            String value = element.getValue();
            int cursorX = textX + client.textRenderer.getWidth(value);
            int cursorColor = parseColor(element.getTextColor(), element.getOpacity());
            context.fill(cursorX, textY, cursorX + 1, textY + textHeight, cursorColor);
        }
    }

    private int parseColor(String colorStr, double opacity) {
        if (colorStr == null || !colorStr.startsWith("#")) {
            return 0;
        }

        try {
            String hex = colorStr.substring(1);
            long value;

            if (hex.length() == 8) {
                value = Long.parseLong(hex, 16);
            } else {
                value = Long.parseLong(hex, 16);
                if (hex.length() == 3) {
                    long r = (value >> 8) & 0xF;
                    long g = (value >> 4) & 0xF;
                    long b = value & 0xF;
                    value = (r << 20) | (r << 16) | (g << 12) | (g << 8) | (b << 4) | b;
                }
                int alpha = (int) (Math.max(0, Math.min(1, opacity)) * 255);
                value |= (long) alpha << 24;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid color format: {}", colorStr);
            return 0;
        }
    }
}