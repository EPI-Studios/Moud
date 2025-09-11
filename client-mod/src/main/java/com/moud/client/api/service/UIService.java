package com.moud.client.api.service;

import com.moud.client.ui.component.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.graalvm.polyglot.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;

public final class UIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIService.class);
    private final MinecraftClient client;

    private Context jsContext;
    private ExecutorService scriptExecutor;

    public UIService() {
        this.client = MinecraftClient.getInstance();
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
        return this.client.getWindow().getScaledWidth();
    }

    public int getScreenHeight() {
        return this.client.getWindow().getScaledHeight();
    }

    public int getMouseX() {
        return (int) (client.mouse.getX() * getScreenWidth() / client.getWindow().getWidth());
    }

    public int getMouseY() {
        return (int) (client.mouse.getY() * getScreenHeight() / client.getWindow().getHeight());
    }

    public UIComponent createElement(String type) {
        return switch (type.toLowerCase()) {
            case "container" -> createContainer();
            case "text" -> createText("");
            case "button" -> createButton("Button");
            case "input" -> createInput("");
            case "image" -> createImage("");
            default -> new UIComponent(type, this);
        };
    }

    public UIContainer createContainer() {
        return new UIContainer(this);
    }

    public UIText createText(String content) {
        return new UIText(content, this);
    }

    public UIButton createButton(String text) {
        return new UIButton(text, this);
    }

    public UIImage createImage(String source) {
        return new UIImage(source, this);
    }

    public UIInput createInput(String placeholder) {
        return new UIInput(placeholder, this);
    }

    public UIScreen createScreen(String title) {
        return new UIScreen(title, this);
    }

    public void showScreen(UIScreen screen) {
        client.execute(() -> client.setScreen(screen));
    }

    public UIComponent positionRelative(UIComponent component, String position) {
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();

        switch (position.toLowerCase()) {
            case "center" -> {
                component.setPosition(
                        (screenWidth - component.getWidth()) / 2,
                        (screenHeight - component.getHeight()) / 2
                );
                return component;
            }
            case "top-left" -> {
                component.setPosition(10, 10);
                return component;
            }
            case "top-right" -> {
                component.setPosition(screenWidth - component.getWidth() - 10, 10);
                return component;
            }
            case "bottom-left" -> {
                component.setPosition(10, screenHeight - component.getHeight() - 10);
                return component;
            }
            case "bottom-right" -> {
                component.setPosition(
                        screenWidth - component.getWidth() - 10,
                        screenHeight - component.getHeight() - 10
                );
                return component;
            }
            case "top-center" -> {
                component.setPosition((screenWidth - component.getWidth()) / 2, 10);
                return component;
            }
            case "bottom-center" -> {
                component.setPosition(
                        (screenWidth - component.getWidth()) / 2,
                        screenHeight - component.getHeight() - 10
                );
                return component;
            }
            default -> {
                return component;
            }
        }
    }

    public UIComponent setPositionPercent(UIComponent component, double xPercent, double yPercent) {
        int x = (int) (getScreenWidth() * xPercent / 100.0);
        int y = (int) (getScreenHeight() * yPercent / 100.0);
        component.setPosition(x, y);
        return component;
    }

    public UIComponent setSizePercent(UIComponent component, double widthPercent, double heightPercent) {
        int width = (int) (getScreenWidth() * widthPercent / 100.0);
        int height = (int) (getScreenHeight() * heightPercent / 100.0);
        component.setSize(width, height);
        return component;
    }

    public UIContainer createGrid(int columns, int rows) {
        UIContainer grid = createContainer();
        grid.setSize(getScreenWidth() - 20, getScreenHeight() - 20);
        grid.setPosition(10, 10);

        int cellWidth = (grid.getWidth() - (int)((columns - 1) * 10)) / columns;
        int cellHeight = (grid.getHeight() - (int)((rows - 1) * 10)) / rows;

        for (int row = 0; row < rows; row++) {
            UIContainer rowContainer = createContainer();
            rowContainer.setFlexDirection("row");
            rowContainer.setGap(10);
            rowContainer.setSize(grid.getWidth(), cellHeight);

            for (int col = 0; col < columns; col++) {
                UIContainer cell = createContainer();
                cell.setSize(cellWidth, cellHeight);
                cell.setBorder(1, "#CCCCCC");
                rowContainer.appendChild(cell);
            }

            grid.appendChild(rowContainer);
        }

        grid.setFlexDirection("column");
        grid.setGap(10);
        return grid;
    }

    public UIComponent animateToPosition(UIComponent component, int targetX, int targetY, int durationMs) {
        long startTime = System.currentTimeMillis();
        int startX = component.getX();
        int startY = component.getY();

        scheduleAnimation(() -> {
            long elapsed = System.currentTimeMillis() - startTime;
            double progress = Math.min(1.0, (double) elapsed / durationMs);

            progress = easeInOutCubic(progress);

            int currentX = (int) (startX + (targetX - startX) * progress);
            int currentY = (int) (startY + (targetY - startY) * progress);

            component.setPosition(currentX, currentY);

            return progress >= 1.0;
        });

        return component;
    }

    private void scheduleAnimation(AnimationCallback callback) {
        if (scriptExecutor != null) {
            scriptExecutor.execute(() -> callback.update());
        }
    }

    private double easeInOutCubic(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    public void cleanUp() {
        jsContext = null;
        scriptExecutor = null;
        LOGGER.info("UIService cleaned up.");
    }

    @FunctionalInterface
    private interface AnimationCallback {
        boolean update();
    }
}