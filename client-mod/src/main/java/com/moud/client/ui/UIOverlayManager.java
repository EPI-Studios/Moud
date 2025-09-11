package com.moud.client.ui;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.api.service.UIService;
import com.moud.client.ui.component.UIComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UIOverlayManager {
    private static UIOverlayManager instance;
    private final List<UIComponent> overlayElements = new CopyOnWriteArrayList<>();
    private final MinecraftClient client = MinecraftClient.getInstance();

    private UIOverlayManager() {}

    public static synchronized UIOverlayManager getInstance() {
        if (instance == null) {
            instance = new UIOverlayManager();
        }
        return instance;
    }

    public void addOverlayElement(UIComponent element) {
        if (!overlayElements.contains(element)) {
            overlayElements.add(element);
        }
    }

    public void removeOverlayElement(UIComponent element) {
        overlayElements.remove(element);
    }

    public void renderOverlays(DrawContext context, RenderTickCounter tickCounter) {
        if (ClientAPIService.INSTANCE == null || ClientAPIService.INSTANCE.ui == null) return;
        UIService uiService = ClientAPIService.INSTANCE.ui;

        int mouseX = (int) (client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth());
        int mouseY = (int) (client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight());

        uiService.render(context, mouseX, mouseY, tickCounter.getTickDelta(true), this.overlayElements);
    }

    public boolean handleOverlayClick(double mouseX, double mouseY, int button) {
        boolean elementClicked = false;

        for (int i = overlayElements.size() - 1; i >= 0; i--) {
            UIComponent element = overlayElements.get(i);
            if (element.isVisible() && element.isPointInside(mouseX, mouseY)) {
                element.triggerClick(mouseX, mouseY, button);
                UIFocusManager.setFocus(element);
                elementClicked = true;
                break;
            }
        }

        if (!elementClicked) {
            UIFocusManager.clearFocus();
        }

        return elementClicked;
    }

    public void clear() {
        UIFocusManager.clearFocus();
        overlayElements.clear();
    }
}