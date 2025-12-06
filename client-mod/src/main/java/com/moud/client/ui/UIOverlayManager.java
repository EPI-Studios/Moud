package com.moud.client.ui;

import com.moud.client.api.service.ClientAPIService;
import com.moud.client.api.service.UIService;
import com.moud.client.ui.animation.UIAnimationManager;
import com.moud.client.ui.component.UIComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UIOverlayManager {
    private static UIOverlayManager instance;
    private final List<UIComponent> overlayElements = new CopyOnWriteArrayList<>();
    private final MinecraftClient client = MinecraftClient.getInstance();
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;

    private static final Logger LOGGER = LoggerFactory.getLogger(UIOverlayManager.class);
    private long frameCount = 0;

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
        if (overlayElements.remove(element)) {
        }
    }

    public void renderOverlays(DrawContext context, RenderTickCounter tickCounter) {
        if (ClientAPIService.INSTANCE == null || ClientAPIService.INSTANCE.ui == null) return;


        UIAnimationManager.getInstance().update();

        checkForResize();

        UIService uiService = ClientAPIService.INSTANCE.ui;
        int mouseX = uiService.getMouseX();
        int mouseY = uiService.getMouseY();

        List<UIComponent> elementsToRender = getOverlayElements();

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        for (UIComponent element : elementsToRender) {
            if (element.parent == null) {
                element.computeLayout(screenWidth, screenHeight);
            }
        }

        for (UIComponent element : elementsToRender) {
            if (element.isVisible()) {
                element.checkHover(mouseX, mouseY);
            }
        }

        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 500.0f);
        for (UIComponent element : elementsToRender) {
            if (element.isVisible()) {
                element.renderWidget(context, mouseX, mouseY, tickCounter.getTickDelta(true));
            }
        }
        context.getMatrices().pop();
    }

    public boolean handleOverlayClick(double mouseX, double mouseY, int button) {
        int scaledMouseX = (int) (mouseX * client.getWindow().getScaledWidth() / client.getWindow().getWidth());
        int scaledMouseY = (int) (mouseY * client.getWindow().getScaledHeight() / client.getWindow().getHeight());

        boolean elementClicked = false;

        List<UIComponent> currentElements = getOverlayElements();
        for (int i = currentElements.size() - 1; i >= 0; i--) {
            UIComponent element = currentElements.get(i);
            if (element.isVisible() && element.mouseClicked(scaledMouseX, scaledMouseY, button)) {
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
        for (UIComponent element : getOverlayElements()) {
            if (element instanceof com.moud.client.ui.component.UIContainer container) {
                if (container.parent == null) {
                    container.updateLayout();
                }
            }
        }
    }

    public void clear() {
        UIFocusManager.clearFocus();
        overlayElements.clear();
        lastScreenWidth = -1;
        lastScreenHeight = -1;
        LOGGER.info("[UI DEBUG] All UI elements cleared.");
    }

    public List<UIComponent> getOverlayElements() {
        return new CopyOnWriteArrayList<>(overlayElements);
    }
}
