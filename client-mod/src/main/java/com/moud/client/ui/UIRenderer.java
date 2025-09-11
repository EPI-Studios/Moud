package com.moud.client.ui;

import com.moud.client.ui.component.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UIRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIRenderer.class);
    private final MinecraftClient client = MinecraftClient.getInstance();

    public void renderComponent(DrawContext context, UIComponent component) {
        if (!component.isVisible()) {
            return;
        }

        if (component instanceof UIImage image) {
            renderImage(context, image);
        } else {

            renderDefaultComponent(context, component);
        }

        for (UIComponent child : component.getChildren()) {
            renderComponent(context, child);
        }
    }

    private void renderDefaultComponent(DrawContext context, UIComponent component) {
        int x = (int) component.getX();
        int y = (int) component.getY();
        int width = (int) component.getWidth();
        int height = (int) component.getHeight();

        int bgColor = parseColor(component.getBackgroundColor(), component.getOpacity());
        int borderColor = parseColor(component.getBorderColor(), component.getOpacity());
        int borderWidth = component.getBorderWidth();

        context.fill(x, y, x + width, y + height, bgColor);

        if (borderWidth > 0) {
            context.drawBorder(x, y, width, height, borderColor);
        }

        renderText(context, component);

        if (component instanceof UIInput input && input.isFocused()) {
            renderCursor(context, input);
        }
    }

    private void renderText(DrawContext context, UIComponent component) {
        String text = component.getText();
        if (text == null || text.isEmpty()) {
            return;
        }

        int x = (int) component.getX();
        int y = (int) component.getY();
        int width = (int) component.getWidth();
        int height = (int) component.getHeight();
        int textColor = parseColor(component.getTextColor(), component.getOpacity());

        int textWidth = client.textRenderer.getWidth(text);
        int textY = y + (height - client.textRenderer.fontHeight) / 2;
        int textX;

        switch (component.getTextAlign().toLowerCase()) {
            case "center":
                textX = x + (width - textWidth) / 2;
                break;
            case "right":
                textX = x + width - textWidth - (int) component.getPaddingRight();
                break;
            default:
                textX = x + (int) component.getPaddingLeft();
                break;
        }
        context.drawText(client.textRenderer, Text.literal(text), textX, textY, textColor, true);
    }

    private void renderCursor(DrawContext context, UIInput input) {

        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            String textBeforeCursor = input.getValue();
            int cursorX = (int) input.getX() + (int) input.getPaddingLeft() + client.textRenderer.getWidth(textBeforeCursor);
            int cursorY = (int) input.getY() + (int) (input.getHeight() - client.textRenderer.fontHeight) / 2;
            int cursorColor = parseColor(input.getTextColor(), input.getOpacity());

            context.fill(cursorX, cursorY, cursorX + 1, cursorY + client.textRenderer.fontHeight, cursorColor);
        }
    }

    private void renderImage(DrawContext context, UIImage image) {
        try {
            Identifier textureId = Identifier.of(image.getSource());
            int x = (int) image.getX();
            int y = (int) image.getY();
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();

            context.drawTexture(textureId, x, y, 0, 0, width, height, width, height);
        } catch (Exception e) {
            LOGGER.error("Failed to render image with source: {}", image.getSource(), e);

            int x = (int) image.getX();
            int y = (int) image.getY();
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            context.fill(x, y, x + width, y + height, 0xFFFF00FF);
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