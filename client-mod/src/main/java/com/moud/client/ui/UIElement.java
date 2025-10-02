package com.moud.client.ui;

import com.moud.client.api.service.UIService;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
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
    private String textAlign = "left";

    private double paddingTop = 0;
    private double paddingRight = 0;
    private double paddingBottom = 0;
    private double paddingLeft = 0;

    private double marginTop = 0;
    private double marginRight = 0;
    private double marginBottom = 0;
    private double marginLeft = 0;

    private String relativePosition = null;
    private double xPercent = -1, yPercent = -1;
    private double widthPercent = -1, heightPercent = -1;

    private final Map<String, Value> eventHandlers = new ConcurrentHashMap<>();

    public UIElement(String type, UIService service) {
        this.type = type;
        this.service = service;
    }

    public String getRelativePosition() { return relativePosition; }
    public void setRelativePosition(String pos) {
        this.relativePosition = pos;
        this.xPercent = -1;
        this.yPercent = -1;
    }

    public double getXPercent() { return xPercent; }
    public double getYPercent() { return yPercent; }
    public void setPositionPercent(double x, double y) {
        this.xPercent = x;
        this.yPercent = y;
        this.relativePosition = null;
    }

    public double getWidthPercent() { return widthPercent; }
    public double getHeightPercent() { return heightPercent; }
    public void setSizePercent(double w, double h) {
        this.widthPercent = w;
        this.heightPercent = h;
    }

    @HostAccess.Export
    public UIElement setId(String id) {
        this.id = id;
        return this;
    }

    @HostAccess.Export
    public String getId() {
        return id;
    }

    @HostAccess.Export
    public String getType() {
        return type;
    }

    @HostAccess.Export
    public UIElement setPosition(double x, double y) {
        this.x = (int) x;
        this.y = (int) y;
        return this;
    }

    @HostAccess.Export
    public UIElement setPos(double x, double y) {
        return setPosition(x, y);
    }

    @HostAccess.Export
    public UIElement setSize(double width, double height) {
        this.width = (int) width;
        this.height = (int) height;
        return this;
    }

    @HostAccess.Export
    public UIElement setText(String text) {
        this.text = text;
        return this;
    }

    @HostAccess.Export
    public UIElement setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    @HostAccess.Export
    public UIElement setValue(String value) {
        String oldValue = this.value;
        this.value = value;
        if (!oldValue.equals(value)) {
            triggerValueChange(value, oldValue);
        }
        return this;
    }

    @HostAccess.Export
    public UIElement setBackgroundColor(String color) {
        this.backgroundColor = color;
        return this;
    }

    @HostAccess.Export
    public UIElement setTextColor(String color) {
        this.textColor = color;
        return this;
    }

    @HostAccess.Export
    public UIElement setBorderColor(String color) {
        this.borderColor = color;
        return this;
    }

    @HostAccess.Export
    public UIElement setBorderWidth(double width) {
        this.borderWidth = (int) width;
        return this;
    }

    @HostAccess.Export
    public UIElement setOpacity(double opacity) {
        this.opacity = Math.max(0.0, Math.min(1.0, opacity));
        return this;
    }

    @HostAccess.Export
    public UIElement setPadding(double top, double right, double bottom, double left) {
        this.paddingTop = top;
        this.paddingRight = right;
        this.paddingBottom = bottom;
        this.paddingLeft = left;
        return this;
    }

    @HostAccess.Export
    public UIElement setPadding(double padding) {
        return setPadding(padding, padding, padding, padding);
    }

    @HostAccess.Export
    public UIElement setMargin(double top, double right, double bottom, double left) {
        this.marginTop = top;
        this.marginRight = right;
        this.marginBottom = bottom;
        this.marginLeft = left;
        return this;
    }

    @HostAccess.Export
    public UIElement setMargin(double margin) {
        return setMargin(margin, margin, margin, margin);
    }

    @HostAccess.Export
    public UIElement setTextAlign(String align) {
        this.textAlign = align;
        return this;
    }

    @HostAccess.Export
    public UIElement setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    @HostAccess.Export
    public UIElement show() {
        return setVisible(true);
    }

    @HostAccess.Export
    public UIElement hide() {
        return setVisible(false);
    }

    @HostAccess.Export
    public UIElement showAsOverlay() {
        return this;
    }

    @HostAccess.Export
    public UIElement hideOverlay() {
        return this;
    }

    @HostAccess.Export
    public UIElement setWidth(double width) {
        this.width = (int) width;
        return this;
    }

    @HostAccess.Export
    public UIElement setHeight(double height) {
        this.height = (int) height;
        return this;
    }

    @HostAccess.Export
    public UIElement setX(double x) {
        this.x = (int) x;
        return this;
    }

    @HostAccess.Export
    public UIElement setY(double y) {
        this.y = (int) y;
        return this;
    }

    @HostAccess.Export
    public UIElement setBorder(String color, double width) {
        this.borderColor = color;
        this.borderWidth = (int) width;
        return this;
    }

    @HostAccess.Export
    public UIElement setBorder(double width) {
        this.borderWidth = (int) width;
        return this;
    }

    @HostAccess.Export
    public UIElement setColor(String color) {
        return setTextColor(color);
    }

    @HostAccess.Export
    public UIElement setBackground(String color) {
        return setBackgroundColor(color);
    }

    @HostAccess.Export
    public int getX() { return x; }

    @HostAccess.Export
    public int getY() { return y; }

    @HostAccess.Export
    public int getWidth() { return width; }

    @HostAccess.Export
    public int getHeight() { return height; }

    @HostAccess.Export
    public String getText() { return text; }

    @HostAccess.Export
    public String getPlaceholder() { return placeholder; }

    @HostAccess.Export
    public String getValue() { return value; }

    @HostAccess.Export
    public String getBackgroundColor() { return backgroundColor; }

    @HostAccess.Export
    public String getTextColor() { return textColor; }

    @HostAccess.Export
    public String getBorderColor() { return borderColor; }

    @HostAccess.Export
    public int getBorderWidth() { return borderWidth; }

    @HostAccess.Export
    public double getOpacity() { return opacity; }

    @HostAccess.Export
    public boolean isVisible() { return visible; }

    @HostAccess.Export
    public String getTextAlign() { return textAlign; }

    @HostAccess.Export
    public double getPaddingTop() { return paddingTop; }

    @HostAccess.Export
    public double getPaddingRight() { return paddingRight; }

    @HostAccess.Export
    public double getPaddingBottom() { return paddingBottom; }

    @HostAccess.Export
    public double getPaddingLeft() { return paddingLeft; }

    @HostAccess.Export
    public double getMarginTop() { return marginTop; }

    @HostAccess.Export
    public double getMarginRight() { return marginRight; }

    @HostAccess.Export
    public double getMarginBottom() { return marginBottom; }

    @HostAccess.Export
    public double getMarginLeft() { return marginLeft; }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @HostAccess.Export
    public UIElement onClick(Value callback) {
        addEventHandler("click", callback);
        return this;
    }

    @HostAccess.Export
    public UIElement onValueChange(Value callback) {
        addEventHandler("valueChange", callback);
        return this;
    }

    @HostAccess.Export
    public UIElement onHover(Value callback) {
        addEventHandler("hover", callback);
        return this;
    }

    @HostAccess.Export
    public UIElement onFocus(Value callback) {
        addEventHandler("focus", callback);
        return this;
    }

    @HostAccess.Export
    public UIElement onBlur(Value callback) {
        addEventHandler("blur", callback);
        return this;
    }

    @HostAccess.Export
    public UIElement onChange(Value callback) {
        return onValueChange(callback);
    }

    @HostAccess.Export
    public UIElement on(String eventType, Value callback) {
        addEventHandler(eventType, callback);
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

    public void triggerFocus() {
        executeEventHandler("focus", this);
    }

    public void triggerBlur() {
        executeEventHandler("blur", this);
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