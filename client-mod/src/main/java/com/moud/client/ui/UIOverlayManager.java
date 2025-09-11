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
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;

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
            checkForResize();
        }
    }

    public void removeOverlayElement(UIComponent element) {
        overlayElements.remove(element);
    }

    public void renderOverlays(DrawContext context, RenderTickCounter tickCounter) {
        if (ClientAPIService.INSTANCE == null || ClientAPIService.INSTANCE.ui == null) return;

        checkForResize();

        UIService uiService = ClientAPIService.INSTANCE.ui;
        int mouseX = uiService.getMouseX();
        int mouseY = uiService.getMouseY();

        for (UIComponent element : overlayElements) {
            if (element.visible) {
                element.renderWidget(context, mouseX, mouseY, tickCounter.getTickDelta(true));
            }
        }
    }

    public boolean handleOverlayClick(double mouseX, double mouseY, int button) {

        int scaledMouseX = (int) (mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth());
        int scaledMouseY = (int) (mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight());

        boolean elementClicked = false;

        for (int i = overlayElements.size() - 1; i >= 0; i--) {
            UIComponent element = overlayElements.get(i);
            if (element.visible && element.mouseClicked(scaledMouseX, scaledMouseY, button)) {
                elementClicked = true;
                break;
            }
        }

        if (!elementClicked) {

            UIFocusManager.clearFocus();
        }

        return elementClicked;
    }

    public boolean handleOverlayKeyPress(int keyCode, int scanCode, int modifiers) {
        UIComponent focused = UIFocusManager.getFocusedComponent();
        if (focused != null && overlayElements.contains(focused)) {

            return true;
        }
        return false;
    }

    public boolean handleOverlayCharTyped(char chr, int modifiers) {
        UIComponent focused = UIFocusManager.getFocusedComponent();
        if (focused != null && overlayElements.contains(focused)) {

            return true;
        }
        return false;
    }

    private void checkForResize() {
        int currentWidth = client.getWindow().getScaledWidth();
        int currentHeight = client.getWindow().getScaledHeight();

        if (lastScreenWidth != currentWidth || lastScreenHeight != currentHeight) {
            onScreenResize(currentWidth, currentHeight);
            lastScreenWidth = currentWidth;
            lastScreenHeight = currentHeight;
        }
    }

    private void onScreenResize(int newWidth, int newHeight) {

        for (UIComponent element : overlayElements) {
            if (element.parent == null) {
                updateElementForResize(element, newWidth, newHeight);
            }
        }
    }

    private void updateElementForResize(UIComponent element, int screenWidth, int screenHeight) {

        Object positionType = element.getClass().getAnnotation(PositionType.class);

        if (element instanceof com.moud.client.ui.component.UIContainer container) {
            container.updateLayout();
        }

        for (UIComponent child : element.getChildren()) {
            updateElementForResize(child, screenWidth, screenHeight);
        }
    }

    public void clear() {
        UIFocusManager.clearFocus();
        overlayElements.clear();
        lastScreenWidth = -1;
        lastScreenHeight = -1;
    }

    public List<UIComponent> getOverlayElements() {
        return new CopyOnWriteArrayList<>(overlayElements);
    }

    public @interface PositionType {
        String value() default "absolute";
    }
}