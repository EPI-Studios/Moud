package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
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

public class UIComponent extends ClickableWidget implements Drawable, Element, Selectable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIComponent.class);

    protected final String type;
    protected final UIService service;
    protected String componentId;

    protected final Map<String, Value> eventHandlers = new ConcurrentHashMap<>();
    protected final List<UIComponent> children = new CopyOnWriteArrayList<>();
    public UIComponent parent;

    protected String backgroundColor = "#FFFFFF";
    protected String textColor = "#000000";
    protected String borderColor = "#000000";
    protected int borderWidth = 0;
    protected double opacity = 1.0;
    protected String textAlign = "left";
    protected double paddingTop = 2, paddingRight = 4, paddingBottom = 2, paddingLeft = 4;
    protected boolean dirty = true;

    public UIComponent(String type, UIService service, int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
        this.type = type;
        this.service = service;
        this.componentId = UUID.randomUUID().toString();
    }

    public UIComponent(String type, UIService service) {
        this(type, service, 0, 0, 100, 20, Text.literal(""));
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        renderBackground(context);
        renderBorder(context);
        renderText(context);
        renderChildren(context, mouseX, mouseY, delta);
    }

    protected void renderBackground(DrawContext context) {
        int bgColor = parseColor(backgroundColor, opacity);
        if ((bgColor >>> 24) > 0) {
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        }
    }

    protected void renderBorder(DrawContext context) {
        if (borderWidth > 0) {
            int borderCol = parseColor(borderColor, opacity);
            for (int i = 0; i < borderWidth; i++) {
                context.drawBorder(getX() - i, getY() - i, getWidth() + 2 * i, getHeight() + 2 * i, borderCol);
            }
        }
    }

    protected void renderText(DrawContext context) {
        if (getMessage().getString().isEmpty()) return;

        int textCol = parseColor(textColor, opacity);
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(getMessage());
        int textHeight = MinecraftClient.getInstance().textRenderer.fontHeight;

        int textX = calculateTextX(textWidth);
        int textY = getY() + (getHeight() - textHeight) / 2;

        context.drawText(MinecraftClient.getInstance().textRenderer, getMessage(), textX, textY, textCol, true);
    }

    protected int calculateTextX(int textWidth) {
        return switch (textAlign.toLowerCase()) {
            case "center" -> getX() + (getWidth() - textWidth) / 2;
            case "right" -> getX() + getWidth() - textWidth - (int) paddingRight;
            default -> getX() + (int) paddingLeft;
        };
    }

    protected void renderChildren(DrawContext context, int mouseX, int mouseY, float delta) {
        for (UIComponent child : children) {
            if (child.visible) {
                child.renderWidget(context, mouseX, mouseY, delta);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !active) return false;

        for (int i = children.size() - 1; i >= 0; i--) {
            UIComponent child = children.get(i);
            if (child.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (isMouseOver(mouseX, mouseY)) {
            triggerClick(mouseX, mouseY, button);
            return true;
        }

        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth() &&
                mouseY >= getY() && mouseY < getY() + getHeight();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            triggerFocus();
        } else {
            triggerBlur();
        }
    }

    public UIComponent setComponentId(String id) {
        this.componentId = id;
        return this;
    }

    public String getComponentId() {
        return componentId;
    }

    public UIComponent setText(String text) {
        setMessage(Text.literal(text));
        return this;
    }

    public String getText() {
        return getMessage().getString();
    }

    public UIComponent setPos(int x, int y) {
        setX(x);
        setY(y);
        updateChildrenPositions();
        markDirty();
        return this;
    }

    public UIComponent setSize(int width, int height) {
        setWidth(width);
        setHeight(height);
        updateChildrenPositions();
        markDirty();
        return this;
    }

    public UIComponent setBackgroundColor(String color) {
        this.backgroundColor = color;
        markDirty();
        return this;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public UIComponent setTextColor(String color) {
        this.textColor = color;
        markDirty();
        return this;
    }

    public String getTextColor() {
        return textColor;
    }

    public UIComponent setBorder(int width, String color) {
        this.borderWidth = width;
        this.borderColor = color;
        markDirty();
        return this;
    }

    public int getBorderWidth() {
        return borderWidth;
    }

    public String getBorderColor() {
        return borderColor;
    }

    public UIComponent setOpacity(double opacity) {
        this.opacity = Math.max(0.0, Math.min(1.0, opacity));
        markDirty();
        return this;
    }

    public double getOpacity() {
        return opacity;
    }

    public UIComponent setTextAlign(String align) {
        this.textAlign = align;
        markDirty();
        return this;
    }

    public String getTextAlign() {
        return textAlign;
    }

    public UIComponent setPadding(double top, double right, double bottom, double left) {
        this.paddingTop = top;
        this.paddingRight = right;
        this.paddingBottom = bottom;
        this.paddingLeft = left;
        markDirty();
        return this;
    }

    public double getPaddingTop() { return paddingTop; }
    public double getPaddingRight() { return paddingRight; }
    public double getPaddingBottom() { return paddingBottom; }
    public double getPaddingLeft() { return paddingLeft; }

    public UIComponent appendChild(UIComponent child) {
        children.add(child);
        child.parent = this;
        updateChildrenPositions();
        markDirty();
        return this;
    }

    public UIComponent removeChild(UIComponent child) {
        children.remove(child);
        child.parent = null;
        markDirty();
        return this;
    }

    public List<UIComponent> getChildren() {
        return new CopyOnWriteArrayList<>(children);
    }

    public UIComponent show() {
        this.visible = true;
        markDirty();
        return this;
    }

    public UIComponent hide() {
        this.visible = false;
        markDirty();
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    public UIComponent onClick(Value callback) {
        addEventHandler("click", callback);
        return this;
    }

    public UIComponent onHover(Value callback) {
        addEventHandler("hover", callback);
        return this;
    }

    public UIComponent onFocus(Value callback) {
        addEventHandler("focus", callback);
        return this;
    }

    public UIComponent onBlur(Value callback) {
        addEventHandler("blur", callback);
        return this;
    }

    protected void addEventHandler(String eventType, Value callback) {
        if (callback != null && callback.canExecute()) {
            eventHandlers.put(eventType, callback);
        }
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

    public void markDirty() {
        this.dirty = true;
        if (parent != null) {
            parent.markDirty();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    protected int parseColor(String colorStr, double opacity) {
        if (colorStr == null || !colorStr.startsWith("#")) {
            return 0;
        }

        try {
            String hex = colorStr.substring(1);
            long value;

            if (hex.length() == 8) {
                value = Long.parseLong(hex, 16);
            } else {
                value = Long.parseLong(hex, 16);
                if (hex.length() == 3) {
                    long r = (value >> 8) & 0xF;
                    long g = (value >> 4) & 0xF;
                    long b = value & 0xF;
                    value = (r << 20) | (r << 16) | (g << 12) | (g << 8) | (b << 4) | b;
                }
                int alpha = (int) (Math.max(0, Math.min(1, opacity)) * 255);
                value |= (long) alpha << 24;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public SelectionType getType() {
        return SelectionType.HOVERED;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }
}