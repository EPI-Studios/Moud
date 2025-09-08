package com.moud.client.ui;

import com.moud.client.api.service.UIService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class UIOverlayManager {
    private static UIOverlayManager instance;
    private final List<UIService.UIElement> overlayElements = new CopyOnWriteArrayList<>();
    private final MinecraftClient client = MinecraftClient.getInstance();

    private UIOverlayManager() {}

    public static UIOverlayManager getInstance() {
        if (instance == null) {
            instance = new UIOverlayManager();
        }
        return instance;
    }

    public void addOverlayElement(UIService.UIElement element) {
        overlayElements.add(element);
    }

    public void removeOverlayElement(UIService.UIElement element) {
        overlayElements.remove(element);
    }

    public void renderOverlays(DrawContext context, RenderTickCounter tickDelta) {
        int mouseX = (int) client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
        int mouseY = (int) client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();

        for (UIService.UIElement element : overlayElements) {
            if (element.isVisible()) {
                renderOverlayElement(context, element, mouseX, mouseY);
            }
        }
    }

    public void handleOverlayClick(double mouseX, double mouseY, int button) {
        for (UIService.UIElement element : overlayElements) {
            if (element.isVisible() && element.isPointInside(mouseX, mouseY)) {
                element.triggerClick(mouseX, mouseY, button);
                break;
            }
        }
    }

    private void renderOverlayElement(DrawContext context, UIService.UIElement element, int mouseX, int mouseY) {
        int x = (int) element.getX();
        int y = (int) element.getY();
        int width = (int) element.getWidth();
        int height = (int) element.getHeight();

        int bgColor = parseColor(element.getBackgroundColor(), element.getOpacity());
        int textColor = parseColor(element.getTextColor(), 1.0);
        int borderColor = parseColor(element.getBorderColor(), 1.0);

        context.fill(x, y, x + width, y + height, bgColor);

        if (element.getBorderWidth() > 0) {
            int bw = element.getBorderWidth();
            context.fill(x, y, x + width, y + bw, borderColor);
            context.fill(x, y + height - bw, x + width, y + height, borderColor);
            context.fill(x, y, x + bw, y + height, borderColor);
            context.fill(x + width - bw, y, x + width, y + height, borderColor);
        }

        if (!element.getText().isEmpty()) {
            int textX = x + 5;
            int textY = y + (height - 8) / 2;
            context.drawText(client.textRenderer, element.getText(), textX, textY, textColor, false);
        }

        for (UIService.UIElement child : element.getChildren()) {
            renderOverlayElement(context, child, mouseX, mouseY);
        }
    }

    private int parseColor(String color, double opacity) {
        int alpha = (int) (opacity * 255) << 24;
        if (color.startsWith("#")) {
            return Integer.parseInt(color.substring(1), 16) | alpha;
        }
        return 0xFFFFFF | alpha;
    }

    public void clear() {
        overlayElements.clear();
    }
}