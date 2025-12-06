package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;
import com.moud.client.ui.UIAnchor;
import com.moud.client.ui.UIRelativePosition;
import com.moud.client.ui.animation.UIAnimation;
import com.moud.client.ui.animation.UIAnimationManager;
import com.moud.client.ui.animation.EasingFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIComponent.class);
    private static final AtomicLong idCounter = new AtomicLong(0);
    private final long uniqueId = idCounter.incrementAndGet();

    protected final String type;
    protected final UIService service;
    protected volatile String componentId;

    protected final Map<String, Value> eventHandlers = new ConcurrentHashMap<>();
    protected final List<UIComponent> children = new CopyOnWriteArrayList<>();
    public UIComponent parent;

    protected volatile float x, y, width, height;
    protected volatile float screenX, screenY;
    protected volatile String backgroundColor = "#00000000";
    protected volatile String textColor = "#000000";
    protected volatile String borderColor = "#000000";
    protected volatile int borderWidth = 0;
    protected volatile double opacity = 1.0;
    protected volatile String textAlign = "left";
    protected volatile double paddingTop = 2, paddingRight = 4, paddingBottom = 2, paddingLeft = 4;
    protected volatile boolean visible = true;
    protected volatile Text message = Text.empty();
    protected volatile boolean focused = false;

    protected volatile String backgroundTexture = null;
    protected volatile Identifier backgroundTextureId = null;

    protected volatile UIAnchor anchor = UIAnchor.TOP_LEFT;
    protected volatile String relativeToId = null;
    protected volatile UIRelativePosition relativePosition = null;
    protected volatile float scaleX = 1.0f;
    protected volatile float scaleY = 1.0f;
    protected volatile boolean serverControlled = false;
    protected volatile boolean fullscreen = false;

    public UIComponent(String type, UIService service) {
        this.type = type;
        this.service = service;
        this.x = 0;
        this.y = 0;
        this.width = 100;
        this.height = 20;
        this.message = Text.literal("");
        this.componentId = java.util.UUID.randomUUID().toString();
    }

    public String getDebugIdentifier() {
        String text = this.message.getString();
        if (text.length() > 15) {
            text = text.substring(0, 12) + "...";
        }
        return String.format("%s[id:%d, text:'%s']", this.type, this.uniqueId, text.isEmpty() ? "N/A" : text);
    }

    public void computeLayout(int screenWidth, int screenHeight) {
        if (parent == null) {

            if (fullscreen) {
                this.x = 0;
                this.y = 0;
                this.width = screenWidth;
                this.height = screenHeight;
                this.anchor = UIAnchor.TOP_LEFT;
            }

            float effectiveWidth = getEffectiveWidth();
            float effectiveHeight = getEffectiveHeight();

            float baseX = switch (anchor) {
                case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> x;
                case TOP_CENTER, CENTER_CENTER, BOTTOM_CENTER -> (screenWidth / 2f) + x - (effectiveWidth / 2f);
                case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> screenWidth - x - effectiveWidth;
            };

            float baseY = switch (anchor) {
                case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> y;
                case CENTER_LEFT, CENTER_CENTER, CENTER_RIGHT -> (screenHeight / 2f) + y - (effectiveHeight / 2f);
                case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenHeight - y - effectiveHeight;
            };

            if (relativeToId != null && relativePosition != null) {
                UIComponent relativeTo = service.getElement(relativeToId);
                if (relativeTo != null) {

                    if (relativeTo.parent == null) relativeTo.computeLayout(screenWidth, screenHeight);

                    switch (relativePosition) {
                        case RIGHT_OF -> { baseX = relativeTo.screenX + relativeTo.getEffectiveWidth() + x; baseY = relativeTo.screenY + y; }
                        case LEFT_OF -> { baseX = relativeTo.screenX - effectiveWidth + x; baseY = relativeTo.screenY + y; }
                        case BELOW -> { baseX = relativeTo.screenX + x; baseY = relativeTo.screenY + relativeTo.getEffectiveHeight() + y; }
                        case ABOVE -> { baseX = relativeTo.screenX + x; baseY = relativeTo.screenY - effectiveHeight + y; }
                    }
                }
            }
            this.screenX = baseX;
            this.screenY = baseY;
        } else {

            this.screenX = parent.screenX + this.x;
            this.screenY = parent.screenY + this.y;
        }

        for (UIComponent child : children) {
            child.computeLayout(screenWidth, screenHeight);
        }
    }

    public float getEffectiveWidth() {
        return width * scaleX;
    }

    public float getEffectiveHeight() {
        return height * scaleY;
    }

    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        if (opacity <= 0.01) return;

        context.getMatrices().push();
        context.getMatrices().translate(screenX, screenY, 0);
        context.getMatrices().scale(scaleX, scaleY, 1.0f);

        if (backgroundTextureId != null) {
            context.setShaderColor(1.0f, 1.0f, 1.0f, (float) opacity);
            context.drawTexture(
                backgroundTextureId,
                0, 0,                          // screen position
                0, 0,                          // UV start
                Math.round(width), Math.round(height),  // screen size
                256, 256                       // actual texture dimensions
            );
            context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            int bgColor = parseColor(backgroundColor, opacity);
            if ((bgColor >>> 24) > 0) {
                context.fill(0, 0, Math.round(width), Math.round(height), bgColor);
            }
        }

        if (borderWidth > 0) {
            int borderCol = parseColor(borderColor, opacity);
            if ((borderCol >>> 24) > 0) {
                for (int i = 0; i < borderWidth; i++) {
                    context.drawBorder(i, i, Math.round(width) - i * 2, Math.round(height) - i * 2, borderCol);
                }
            }
        }

        renderText(context);

        for (UIComponent child : children) {

            context.getMatrices().push();
            context.getMatrices().translate(-screenX, -screenY, 0);
            child.renderWidget(context, mouseX, mouseY, delta);
            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }

    protected void renderText(DrawContext context) {
        if (message.getString().isEmpty()) return;

        int textCol = parseColor(textColor, opacity);
        if ((textCol >>> 24) <= 0) return;

        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(message);
        int textHeight = MinecraftClient.getInstance().textRenderer.fontHeight;

        int textX = switch (textAlign.toLowerCase()) {
            case "center" -> (int) Math.round((width - textWidth) / 2f);
            case "right" -> (int) Math.round(width - textWidth - paddingRight);
            default -> (int) Math.round(paddingLeft);
        };
        int textY = (int) Math.round((height - textHeight) / 2f);

        context.drawText(MinecraftClient.getInstance().textRenderer, message, textX, textY, textCol, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || opacity <= 0.01) return false;

        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (mouseX >= screenX && mouseX < screenX + getEffectiveWidth() && mouseY >= screenY && mouseY < screenY + getEffectiveHeight()) {
            triggerClick(mouseX, mouseY, button);
            return true;
        }

        return false;
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
    public UIComponent setAnchor(String anchorName) {
        try {
            this.anchor = UIAnchor.valueOf(anchorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid anchor: {}", anchorName);
        }
        return this;
    }

    @HostAccess.Export
    public UIComponent relativeTo(String targetId, String position) {
        this.relativeToId = targetId;
        try {
            this.relativePosition = UIRelativePosition.valueOf(position.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid relative position: {}", position);
        }
        return this;
    }

    @HostAccess.Export
    public UIComponent setScale(double scaleX, double scaleY) {
        this.scaleX = (float) scaleX;
        this.scaleY = (float) scaleY;
        return this;
    }

    @HostAccess.Export
    public UIComponent showAsOverlay() {
        com.moud.client.ui.UIOverlayManager.getInstance().addOverlayElement(this);
        this.visible = true;
        return this;
    }

    @HostAccess.Export
    public UIComponent hideOverlay() {
        this.visible = false;
        return this;
    }

    @HostAccess.Export
    public float getX() { return x; }

    @HostAccess.Export
    public float getY() { return y; }

    @HostAccess.Export
    public float getWidth() { return width; }

    @HostAccess.Export
    public float getHeight() { return height; }

    @HostAccess.Export
    public UIComponent setX(double x) {
        this.x = (float) x;
        return this;
    }

    @HostAccess.Export
    public UIComponent setY(double y) {
        this.y = (float) y;
        return this;
    }

    @HostAccess.Export
    public UIComponent setWidth(double width) {
        this.width = (float) width;
        return this;
    }

    @HostAccess.Export
    public UIComponent setHeight(double height) {
        this.height = (float) height;
        return this;
    }

    @HostAccess.Export
    public String getComponentId() { return componentId; }

    @HostAccess.Export
    public UIComponent setComponentId(String id) {
        this.componentId = id;
        return this;
    }

    public boolean isServerControlled() {
        return serverControlled;
    }

    public UIComponent setServerControlled(boolean serverControlled) {
        this.serverControlled = serverControlled;
        return this;
    }

    @HostAccess.Export
    public boolean isFullscreen() {
        return fullscreen;
    }

    @HostAccess.Export
    public UIComponent setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        return this;
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
        this.x = (float) x;
        this.y = (float) y;
        return this;
    }

    @HostAccess.Export
    public UIComponent setSize(double width, double height) {
        this.width = (float) width;
        this.height = (float) height;
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
    public UIComponent setBackgroundTexture(String texture) {
        this.backgroundTexture = texture;
        try {
            this.backgroundTextureId = texture != null ? net.minecraft.util.Identifier.tryParse(texture) : null;
            if (texture != null && this.backgroundTextureId == null) {
                LOGGER.warn("Invalid texture identifier: {}", texture);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse texture identifier: {}", texture, e);
            this.backgroundTextureId = null;
        }
        return this;
    }

    @HostAccess.Export
    public String getBackgroundTexture() {
        return backgroundTexture;
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
        return this;
    }

    @HostAccess.Export
    public UIComponent hide() {
        this.visible = false;
        return this;
    }

    @HostAccess.Export
    public boolean isVisible() {
        return visible;
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

    private boolean wasHovering = false;

    public void checkHover(double mouseX, double mouseY) {
        if (!visible || opacity <= 0.01) {
            if (wasHovering) {
                triggerUnhover(mouseX, mouseY);
                wasHovering = false;
            }
            return;
        }

        boolean isHovering = mouseX >= screenX && mouseX < screenX + getEffectiveWidth() &&
                mouseY >= screenY && mouseY < screenY + getEffectiveHeight();

        if (isHovering && !wasHovering) {
            triggerHover(mouseX, mouseY);
            wasHovering = true;
        } else if (!isHovering && wasHovering) {
            triggerUnhover(mouseX, mouseY);
            wasHovering = false;
        }

        for (UIComponent child : children) {
            child.checkHover(mouseX, mouseY);
        }
    }

    public void triggerHover(double mouseX, double mouseY) {
        if (serverControlled) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("mouseX", mouseX);
            payload.put("mouseY", mouseY);
            notifyServerInteraction("hover", payload);
        }
        executeEventHandler("hover", this, mouseX, mouseY);
    }

    public void triggerUnhover(double mouseX, double mouseY) {
        if (serverControlled) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("mouseX", mouseX);
            payload.put("mouseY", mouseY);
            notifyServerInteraction("unhover", payload);
        }
        executeEventHandler("unhover", this, mouseX, mouseY);
    }

    @HostAccess.Export
    public UIComponent onUnhover(Value callback) {
        if (callback != null && callback.canExecute()) {
            eventHandlers.put("unhover", callback);
        }
        return this;
    }

    public void triggerClick(double mouseX, double mouseY, int button) {
        if (serverControlled) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("mouseX", mouseX);
            payload.put("mouseY", mouseY);
            payload.put("button", button);
            notifyServerInteraction("click", payload);
        }
        executeEventHandler("click", this, mouseX, mouseY, button);
    }

    public void triggerFocus() {
        if (serverControlled) {
            notifyServerInteraction("focus", new HashMap<>());
        }
        executeEventHandler("focus", this);
    }

    public void triggerBlur() {
        if (serverControlled) {
            notifyServerInteraction("blur", new HashMap<>());
        }
        executeEventHandler("blur", this);
    }

    protected void executeEventHandler(String eventType, Object... args) {
        Value handler = eventHandlers.get(eventType);
        if (handler != null && service != null && service.getScriptExecutor() != null &&
                !service.getScriptExecutor().isShutdown() && service.getJsContext() != null) {
            service.getScriptExecutor().execute(() -> {
                try {
                    service.getJsContext().enter();
                    handler.execute(args);
                } catch (Exception e) {
                    LOGGER.error("Error executing UI event handler for '{}' on element {}", eventType, componentId, e);
                } finally {
                    try {
                        service.getJsContext().leave();
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    protected void notifyServerInteraction(String eventType, Map<String, Object> payload) {
        if (service != null && serverControlled) {
            service.notifyServerInteraction(this, eventType, payload);
        }
    }

    /**
     * Animate this component's properties smoothly over time
     * @param options Map containing target properties and animation config
     * Example: { x: 100, y: 200, opacity: 0.5, duration: 500, easing: 'ease-out' }
     */
    @HostAccess.Export
    public UIComponent animate(Map<String, Object> options) {
        UIAnimation.AnimationConfig config = new UIAnimation.AnimationConfig();

        // Parse target values
        if (options.containsKey("x")) {
            config.targetX = ((Number) options.get("x")).floatValue();
        }
        if (options.containsKey("y")) {
            config.targetY = ((Number) options.get("y")).floatValue();
        }
        if (options.containsKey("width")) {
            config.targetWidth = ((Number) options.get("width")).floatValue();
        }
        if (options.containsKey("height")) {
            config.targetHeight = ((Number) options.get("height")).floatValue();
        }
        if (options.containsKey("opacity")) {
            config.targetOpacity = ((Number) options.get("opacity")).floatValue();
        }

        // Parse duration
        if (options.containsKey("duration")) {
            config.duration = ((Number) options.get("duration")).longValue();
        }

        // Parse easing
        if (options.containsKey("easing")) {
            String easingStr = options.get("easing").toString().toUpperCase().replace('-', '_');
            try {
                config.easing = EasingFunction.valueOf(easingStr);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown easing function: {}", easingStr);
            }
        }

        // Parse onComplete callback
        if (options.containsKey("onComplete") && options.get("onComplete") instanceof Value) {
            config.onComplete = (Value) options.get("onComplete");
        }

        // Create and register animation
        UIAnimation animation = new UIAnimation(this, config);
        UIAnimationManager.getInstance().addAnimation(animation);

        return this;
    }
}
