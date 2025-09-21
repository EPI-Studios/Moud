package com.moud.client.ui;

import com.moud.client.api.service.UIService;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class UIElement {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIElement.class);

    private final String type;
    private final UIService service;
    private String id;

    private int x = 0;
    private int y = 0;
    private int width = 100;
    private int height = 20;
    private boolean visible = true;

    private String text = "";
    private String placeholder = "";
    private String value = "";
    private String backgroundColor = "#FFFFFF";
    private String textColor = "#000000";
    private String borderColor = "#000000";
    private int borderWidth = 0;
    private double opacity = 1.0;

    private final Map<String, Value> eventHandlers = new ConcurrentHashMap<>();

    public UIElement(String type, UIService service) {
        this.type = type;
        this.service = service;
    }

    public UIElement setId(String id) {
        this.id = id;
        return this;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public UIElement setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public UIElement setSize(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public UIElement setText(String text) {
        this.text = text;
        return this;
    }

    public UIElement setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public UIElement setValue(String value) {
        String oldValue = this.value;
        this.value = value;
        if (!oldValue.equals(value)) {
            triggerValueChange(value, oldValue);
        }
        return this;
    }

    public UIElement setBackgroundColor(String color) {
        this.backgroundColor = color;
        return this;
    }

    public UIElement setTextColor(String color) {
        this.textColor = color;
        return this;
    }

    public UIElement setBorderColor(String color) {
        this.borderColor = color;
        return this;
    }

    public UIElement setBorderWidth(int width) {
        this.borderWidth = width;
        return this;
    }

    public UIElement setOpacity(double opacity) {
        this.opacity = Math.max(0.0, Math.min(1.0, opacity));
        return this;
    }

    public UIElement setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public UIElement show() {
        return setVisible(true);
    }

    public UIElement hide() {
        return setVisible(false);
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getText() { return text; }
    public String getPlaceholder() { return placeholder; }
    public String getValue() { return value; }
    public String getBackgroundColor() { return backgroundColor; }
    public String getTextColor() { return textColor; }
    public String getBorderColor() { return borderColor; }
    public int getBorderWidth() { return borderWidth; }
    public double getOpacity() { return opacity; }
    public boolean isVisible() { return visible; }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public UIElement onClick(Value callback) {
        addEventHandler("click", callback);
        return this;
    }

    public UIElement onValueChange(Value callback) {
        addEventHandler("valueChange", callback);
        return this;
    }

    public UIElement onHover(Value callback) {
        addEventHandler("hover", callback);
        return this;
    }

    private void addEventHandler(String eventType, Value callback) {
        if (callback != null && callback.canExecute()) {
            eventHandlers.put(eventType, callback);
        }
    }

    public void triggerClick(double mouseX, double mouseY, int button) {
        executeEventHandler("click", this, mouseX, mouseY, button);
    }

    public void triggerValueChange(String newValue, String oldValue) {
        executeEventHandler("valueChange", this, newValue, oldValue);
    }

    public void triggerHover(double mouseX, double mouseY) {
        executeEventHandler("hover", this, mouseX, mouseY);
    }

    private void executeEventHandler(String eventType, Object... args) {
        Value handler = eventHandlers.get(eventType);
        ExecutorService executor = service.getScriptExecutor();
        Context context = service.getJsContext();

        if (handler != null && executor != null && !executor.isShutdown() && context != null) {
            executor.execute(() -> {
                try {
                    context.enter();
                    handler.execute(args);
                } catch (Exception e) {
                    LOGGER.error("Error executing UI event handler for '{}' on element {}", eventType, id, e);
                } finally {
                    try {
                        context.leave();
                    } catch (Exception ignored) {}
                }
            });
        }
    }
}