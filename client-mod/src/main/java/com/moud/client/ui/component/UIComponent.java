package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

public class UIComponent implements Drawable, Element, Selectable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIComponent.class);

    protected final String type;
    protected final UIService service;
    protected String componentId;

    protected final Map<String, Value> eventHandlers = new ConcurrentHashMap<>();
    protected final List<UIComponent> children = new CopyOnWriteArrayList<>();
    public UIComponent parent;

    protected int x, y, width, height;
    protected String backgroundColor = "#FFFFFF";
    protected String textColor = "#000000";
    protected String borderColor = "#000000";
    protected int borderWidth = 0;
    protected double opacity = 1.0;
    protected String textAlign = "left";
    protected double paddingTop = 2, paddingRight = 4, paddingBottom = 2, paddingLeft = 4;
    protected boolean visible = true;
    protected boolean active = true;
    protected Text message = Text.empty();
    protected boolean focused = false;

    public UIComponent(String type, UIService service, int x, int y, int width, int height, Text message) {
        this.type = type;
        this.service = service;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message;
        this.componentId = java.util.UUID.randomUUID().toString();
    }

    public UIComponent(String type, UIService service) {
        this(type, service, 0, 0, 100, 20, Text.literal(""));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible || opacity <= 0.01) return;

        int bgColor = parseColor(backgroundColor, opacity);
        if ((bgColor >>> 24) > 0) {
            context.fill(x, y, x + width, y + height, bgColor);
        }

        if (borderWidth > 0) {
            int borderCol = parseColor(borderColor, opacity);
            if ((borderCol >>> 24) > 0) {
                context.drawBorder(x, y, width, height, borderCol);
            }
        }

        if (!message.getString().isEmpty()) {
            int textCol = parseColor(textColor, opacity);
            if ((textCol >>> 24) > 0) {
                int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(message);
                int textHeight = MinecraftClient.getInstance().textRenderer.fontHeight;
                int textX = switch (textAlign.toLowerCase()) {
                    case "center" -> x + (width - textWidth) / 2;
                    case "right" -> x + width - textWidth - (int) paddingRight;
                    default -> x + (int) paddingLeft;
                };
                int textY = y + (height - textHeight) / 2;
                context.drawText(MinecraftClient.getInstance().textRenderer, message, textX, textY, textCol, true);
            }
        }

        for (UIComponent child : children) {
            child.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !active || opacity <= 0.01) return false;

        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            triggerClick(mouseX, mouseY, button);
            return true;
        }

        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        if (focused) {
            triggerFocus();
        } else {
            triggerBlur();
        }
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public SelectionType getType() {
        return SelectionType.HOVERED;
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {
    }

    protected int parseColor(String colorStr, double elementOpacity) {
        if (colorStr == null || !colorStr.startsWith("#")) return 0;

        try {
            long value = Long.parseLong(colorStr.substring(1), 16);
            int alpha, red, green, blue;

            if (colorStr.length() == 9) {
                alpha = (int) ((value >> 24) & 0xFF);
                red = (int) ((value >> 16) & 0xFF);
                green = (int) ((value >> 8) & 0xFF);
                blue = (int) (value & 0xFF);
            } else if (colorStr.length() == 7) {
                alpha = 255;
                red = (int) ((value >> 16) & 0xFF);
                green = (int) ((value >> 8) & 0xFF);
                blue = (int) (value & 0xFF);
            } else {
                return 0;
            }

            int finalAlpha = (int) (alpha * Math.max(0.0, Math.min(1.0, elementOpacity)));
            return (finalAlpha << 24) | (red << 16) | (green << 8) | blue;
        } catch (NumberFormatException e) {
            return 0;
        }
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
    public void setX(int x) { this.x = x; }

    @HostAccess.Export
    public void setY(int y) { this.y = y; }

    @HostAccess.Export
    public void setWidth(int width) { this.width = width; }

    @HostAccess.Export
    public void setHeight(int height) { this.height = height; }

    @HostAccess.Export
    public String getComponentId() { return componentId; }

    @HostAccess.Export
    public UIComponent setComponentId(String id) {
        this.componentId = id;
        return this;
    }

    protected void setMessage(Text message) {
        this.message = message;
    }

    protected Text getMessage() {
        return message;
    }

    @HostAccess.Export
    public UIComponent setText(String text) {
        this.message = Text.literal(text);
        return this;
    }

    @HostAccess.Export
    public String getText() {
        return message.getString();
    }

    @HostAccess.Export
    public UIComponent setPos(double x, double y) {
        this.x = (int) x;
        this.y = (int) y;
        updateChildrenPositions();
        return this;
    }

    @HostAccess.Export
    public UIComponent setSize(double width, double height) {
        this.width = (int) width;
        this.height = (int) height;
        updateChildrenPositions();
        return this;
    }

    @HostAccess.Export
    public UIComponent setBackgroundColor(String color) {
        this.backgroundColor = color;
        return this;
    }

    @HostAccess.Export
    public String getBackgroundColor() {
        return backgroundColor;
    }

    @HostAccess.Export
    public UIComponent setTextColor(String color) {
        this.textColor = color;
        return this;
    }

    @HostAccess.Export
    public String getTextColor() {
        return textColor;
    }

    @HostAccess.Export
    public UIComponent setBorder(int width, String color) {
        this.borderWidth = width;
        this.borderColor = color;
        return this;
    }

    @HostAccess.Export
    public int getBorderWidth() {
        return borderWidth;
    }

    @HostAccess.Export
    public String getBorderColor() {
        return borderColor;
    }

    @HostAccess.Export
    public UIComponent setOpacity(double opacity) {
        this.opacity = Math.max(0.0, Math.min(1.0, opacity));
        return this;
    }

    @HostAccess.Export
    public double getOpacity() {
        return opacity;
    }

    @HostAccess.Export
    public UIComponent setTextAlign(String align) {
        this.textAlign = align;
        return this;
    }

    @HostAccess.Export
    public String getTextAlign() {
        return textAlign;
    }

    @HostAccess.Export
    public UIComponent setPadding(double top, double right, double bottom, double left) {
        this.paddingTop = top;
        this.paddingRight = right;
        this.paddingBottom = bottom;
        this.paddingLeft = left;
        return this;
    }

    @HostAccess.Export
    public double getPaddingTop() { return paddingTop; }

    @HostAccess.Export
    public double getPaddingRight() { return paddingRight; }

    @HostAccess.Export
    public double getPaddingBottom() { return paddingBottom; }

    @HostAccess.Export
    public double getPaddingLeft() { return paddingLeft; }

    @HostAccess.Export
    public UIComponent appendChild(UIComponent child) {
        children.add(child);
        child.parent = this;
        updateChildrenPositions();
        return this;
    }

    @HostAccess.Export
    public UIComponent removeChild(UIComponent child) {
        children.remove(child);
        child.parent = null;
        return this;
    }

    @HostAccess.Export
    public List<UIComponent> getChildren() {
        return new CopyOnWriteArrayList<>(children);
    }

    @HostAccess.Export
    public UIComponent show() {
        this.visible = true;
        this.active = true;
        return this;
    }

    @HostAccess.Export
    public UIComponent hide() {
        this.visible = false;
        this.active = false;
        return this;
    }

    @HostAccess.Export
    public boolean isVisible() {
        return visible;
    }

    @HostAccess.Export
    public UIComponent showAsOverlay() {
        com.moud.client.ui.UIOverlayManager.getInstance().addOverlayElement(this);
        this.visible = true;
        this.active = true;
        return this;
    }

    @HostAccess.Export
    public UIComponent hideOverlay() {
        this.visible = false;
        this.active = false;
        com.moud.client.ui.UIOverlayManager.getInstance().removeOverlayElement(this);
        return this;
    }

    @HostAccess.Export
    public UIComponent onClick(Value callback) {
        if (callback != null && callback.canExecute()) {
            eventHandlers.put("click", callback);
        }
        return this;
    }

    @HostAccess.Export
    public UIComponent onHover(Value callback) {
        if (callback != null && callback.canExecute()) {
            eventHandlers.put("hover", callback);
        }
        return this;
    }

    @HostAccess.Export
    public UIComponent onFocus(Value callback) {
        if (callback != null && callback.canExecute()) {
            eventHandlers.put("focus", callback);
        }
        return this;
    }

    @HostAccess.Export
    public UIComponent onBlur(Value callback) {
        if (callback != null && callback.canExecute()) {
            eventHandlers.put("blur", callback);
        }
        return this;
    }

    public void triggerClick(double mouseX, double mouseY, int button) {
        executeEventHandler("click", this, mouseX, mouseY, button);
    }

    public void triggerFocus() {
        executeEventHandler("focus", this);
    }

    public void triggerBlur() {
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
                    LOGGER.error("Error executing UI event handler for '{}' on element {}", eventType, componentId, e);
                } finally {
                    try {
                        context.leave();
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    protected void updateChildrenPositions() {
    }
}