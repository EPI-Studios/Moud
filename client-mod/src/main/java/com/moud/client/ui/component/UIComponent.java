package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;
import com.moud.client.ui.UIOverlayManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

public class UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIComponent.class);

    protected final String type;
    protected final UIService service;
    protected String id;

    protected final Map<String, Value> eventHandlers = new ConcurrentHashMap<>();
    protected final List<UIComponent> children = new CopyOnWriteArrayList<>();
    protected UIComponent parent;

    protected boolean visible = true;
    protected boolean dirty = true;

    protected double x = 0, y = 0, width = 100, height = 30;

    protected String backgroundColor = "#FFFFFF";
    protected String textColor = "#000000";
    protected String text = "";
    protected int borderWidth = 0;
    protected String borderColor = "#000000";
    protected double opacity = 1.0;
    protected String textAlign = "left";
    protected double paddingTop = 0, paddingRight = 0, paddingBottom = 0, paddingLeft = 0;

    public UIComponent(String type, UIService service) {
        this.type = type;
        this.service = service;
        this.id = UUID.randomUUID().toString();
    }

    public void setId(String id) { this.id = id; }
    public String getId() { return id; }

    public UIComponent setText(String text) { this.text = text; markDirty(); return this; }
    public String getText() { return text; }

    public UIComponent setPosition(double x, double y) { this.x = x; this.y = y; markDirty(); return this; }
    public double getX() { return x; }
    public double getY() { return y; }

    public UIComponent setSize(double width, double height) { this.width = width; this.height = height; markDirty(); return this; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }

    public UIComponent setBackgroundColor(String color) { this.backgroundColor = color; markDirty(); return this; }
    public String getBackgroundColor() { return backgroundColor; }

    public UIComponent setTextColor(String color) { this.textColor = color; markDirty(); return this; }
    public String getTextColor() { return textColor; }

    public UIComponent setBorder(int width, String color) { this.borderWidth = width; this.borderColor = color; markDirty(); return this; }
    public int getBorderWidth() { return borderWidth; }
    public String getBorderColor() { return borderColor; }

    public UIComponent setOpacity(double opacity) { this.opacity = Math.max(0.0, Math.min(1.0, opacity)); markDirty(); return this; }
    public double getOpacity() { return opacity; }

    public UIComponent setTextAlign(String align) { this.textAlign = align; markDirty(); return this; }
    public String getTextAlign() { return textAlign; }

    public UIComponent setPadding(double top, double right, double bottom, double left) { this.paddingTop = top; this.paddingRight = right; this.paddingBottom = bottom; this.paddingLeft = left; markDirty(); return this; }
    public double getPaddingTop() { return paddingTop; }
    public double getPaddingRight() { return paddingRight; }
    public double getPaddingBottom() { return paddingBottom; }
    public double getPaddingLeft() { return paddingLeft; }

    public UIComponent appendChild(UIComponent child) { children.add(child); child.parent = this; markDirty(); return this; }
    public UIComponent removeChild(UIComponent child) { children.remove(child); child.parent = null; markDirty(); return this; }
    public List<UIComponent> getChildren() { return new CopyOnWriteArrayList<>(children); }

    public UIComponent show() { this.visible = true; markDirty(); return this; }
    public UIComponent hide() { this.visible = false; markDirty(); return this; }
    public boolean isVisible() { return visible; }

    public UIComponent showAsOverlay() {
        UIOverlayManager.getInstance().addOverlayElement(this);
        this.visible = true;
        markDirty();
        return this;
    }

    public UIComponent hideOverlay() {
        UIOverlayManager.getInstance().removeOverlayElement(this);
        this.visible = false;
        markDirty();
        return this;
    }

    public UIComponent onClick(Value callback) { addEventHandler("click", callback); return this; }
    public UIComponent onHover(Value callback) { addEventHandler("hover", callback); return this; }
    public UIComponent onFocus(Value callback) { addEventHandler("focus", callback); return this; }
    public UIComponent onBlur(Value callback) { addEventHandler("blur", callback); return this; }

    protected void addEventHandler(String eventType, Value callback) {
        if (callback != null && callback.canExecute()) {
            eventHandlers.put(eventType, callback);
        } else {
            LOGGER.warn("Attempted to register an invalid or non-executable callback for event '{}' on element {}", eventType, id);
        }
    }

    public void triggerClick(double mouseX, double mouseY, int button) {
        executeEventHandler("click", this, mouseX, mouseY, button);
    }

    public void triggerFocus() {
        if (this instanceof UIInput input) {
            input.setFocused(true);
        }
        executeEventHandler("focus", this);
    }

    public void triggerBlur() {
        if (this instanceof UIInput input) {
            input.setFocused(false);
        }
        executeEventHandler("blur", this);
    }

    protected void executeEventHandler(String eventType, Object... args) {
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
                    } catch (Exception e) {

                    }
                }
            });
        }
    }

    public void markDirty() {
        this.dirty = true;
        if (parent != null) {
            parent.markDirty();
        }
    }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { this.dirty = false; }
    public boolean isPointInside(double pX, double pY) {
        return pX >= x && pX <= x + width && pY >= y && pY <= y + height;
    }
}