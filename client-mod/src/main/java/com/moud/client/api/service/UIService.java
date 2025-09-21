package com.moud.client.api.service;

import com.moud.client.ui.UIRenderer;
import com.moud.client.ui.UIElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public final class UIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIService.class);
    private final MinecraftClient client;
    private final UIRenderer renderer;
    private final Map<String, UIElement> elements = new ConcurrentHashMap<>();

    private Context jsContext;
    private ExecutorService scriptExecutor;
    private float totalTickDelta = 0.0f;

    public UIService() {
        this.client = MinecraftClient.getInstance();
        this.renderer = new UIRenderer();
        registerHudRenderer();
    }

    private void registerHudRenderer() {
        HudRenderCallback.EVENT.register((context, tickDeltaManager) -> {
            if (elements.isEmpty()) return;

            totalTickDelta += tickDeltaManager.getTickDelta(true);

            int mouseX = getMouseX();
            int mouseY = getMouseY();

            for (UIElement element : elements.values()) {
                if (element.isVisible()) {
                    renderer.render(context, element, mouseX, mouseY, totalTickDelta);
                }
            }
        });
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void setExecutor(ExecutorService executor) {
        this.scriptExecutor = executor;
    }

    public Context getJsContext() {
        return jsContext;
    }

    public ExecutorService getScriptExecutor() {
        return scriptExecutor;
    }

    public MinecraftClient getMinecraftClient() {
        return client;
    }

    public int getScreenWidth() {
        return client.getWindow().getScaledWidth();
    }

    public int getScreenHeight() {
        return client.getWindow().getScaledHeight();
    }

    public int getMouseX() {
        return (int) (client.mouse.getX() * getScreenWidth() / client.getWindow().getWidth());
    }

    public int getMouseY() {
        return (int) (client.mouse.getY() * getScreenHeight() / client.getWindow().getHeight());
    }

    public UIElement createElement(String type) {
        UIElement element = new UIElement(type, this);
        return element;
    }

    public UIElement createText(String content) {
        UIElement element = createElement("text");
        element.setText(content);
        element.setSize(client.textRenderer.getWidth(content), client.textRenderer.fontHeight);
        return element;
    }

    public UIElement createButton(String text) {
        UIElement element = createElement("button");
        element.setText(text);
        element.setSize(Math.max(80, client.textRenderer.getWidth(text) + 20), 20);
        element.setBackgroundColor("#C0C0C0");
        element.setBorderColor("#808080");
        element.setBorderWidth(1);
        return element;
    }

    public UIElement createInput(String placeholder) {
        UIElement element = createElement("input");
        element.setPlaceholder(placeholder);
        element.setSize(200, 20);
        element.setBackgroundColor("#FFFFFF");
        element.setBorderColor("#CCCCCC");
        element.setBorderWidth(1);
        return element;
    }

    public UIElement createContainer() {
        UIElement element = createElement("container");
        element.setSize(100, 100);
        element.setBackgroundColor("#00000000");
        return element;
    }

    public void addElement(String id, UIElement element) {
        elements.put(id, element);
        element.setId(id);
    }

    public void removeElement(String id) {
        elements.remove(id);
    }

    public UIElement getElement(String id) {
        return elements.get(id);
    }

    public void clearElements() {
        elements.clear();
    }

    public UIElement positionRelative(UIElement element, String position) {
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();

        switch (position.toLowerCase()) {
            case "center" -> element.setPosition(
                    (screenWidth - element.getWidth()) / 2,
                    (screenHeight - element.getHeight()) / 2
            );
            case "top-left" -> element.setPosition(10, 10);
            case "top-right" -> element.setPosition(screenWidth - element.getWidth() - 10, 10);
            case "bottom-left" -> element.setPosition(10, screenHeight - element.getHeight() - 10);
            case "bottom-right" -> element.setPosition(
                    screenWidth - element.getWidth() - 10,
                    screenHeight - element.getHeight() - 10
            );
            case "top-center" -> element.setPosition((screenWidth - element.getWidth()) / 2, 10);
            case "bottom-center" -> element.setPosition(
                    (screenWidth - element.getWidth()) / 2,
                    screenHeight - element.getHeight() - 10
            );
        }
        return element;
    }

    public UIElement setPositionPercent(UIElement element, double xPercent, double yPercent) {
        int x = (int) (getScreenWidth() * xPercent / 100.0);
        int y = (int) (getScreenHeight() * yPercent / 100.0);
        element.setPosition(x, y);
        return element;
    }

    public UIElement setSizePercent(UIElement element, double widthPercent, double heightPercent) {
        int width = (int) (getScreenWidth() * widthPercent / 100.0);
        int height = (int) (getScreenHeight() * heightPercent / 100.0);
        element.setSize(width, height);
        return element;
    }

    public boolean handleClick(double mouseX, double mouseY, int button) {
        for (UIElement element : elements.values()) {
            if (element.isVisible() && element.contains(mouseX, mouseY)) {
                element.triggerClick(mouseX, mouseY, button);
                return true;
            }
        }
        return false;
    }

    public void cleanUp() {
        elements.clear();
        jsContext = null;
        scriptExecutor = null;
        LOGGER.info("UIService cleaned up.");
    }
}