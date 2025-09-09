package com.moud.client.api.service;

import com.moud.client.ui.UIOverlayManager;
import com.moud.client.ui.layout.LayoutManager;
import com.moud.client.ui.animation.AnimationEngine;
import com.moud.client.ui.theme.ThemeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public final class UIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIService.class);
    private Context jsContext;
    private ExecutorService scriptExecutor;
    private final MinecraftClient client;
    private final Map<String, UIElement> elements = new ConcurrentHashMap<>();
    private final List<UIElement> renderQueue = new CopyOnWriteArrayList<>();
    private final UIRenderer renderer = new UIRenderer();
    private final LayoutManager layoutManager = new LayoutManager();
    private final AnimationEngine animationEngine = new AnimationEngine();
    private final ThemeManager themeManager = new ThemeManager();

    public UIService() {
        this.client = MinecraftClient.getInstance();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void setExecutor(ExecutorService executor) {
        this.scriptExecutor = executor;
    }

    public UIElement createElement(String type) {
        UIElement element = new UIElement(type, this);
        String id = UUID.randomUUID().toString();
        elements.put(id, element);
        element.setId(id);
        return element;
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

    public UIInput createInput(String placeholder) {
        return new UIInput(placeholder, this);
    }

    public UIScreen createScreen(String title) {
        return new UIScreen(title, this);
    }

    public void showScreen(UIScreen screen) {
        client.setScreen(screen);
    }

    public void addToRenderQueue(UIElement element) {
        if (!renderQueue.contains(element)) {
            renderQueue.add(element);
        }
    }

    public void removeFromRenderQueue(UIElement element) {
        renderQueue.remove(element);
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        animationEngine.update(delta);

        for (UIElement element : renderQueue) {
            if (element.isVisible()) {
                layoutManager.updateLayout(element);
                renderer.renderElement(context, element, mouseX, mouseY);
            }
        }
    }

    public void handleClick(double mouseX, double mouseY, int button) {
        for (UIElement element : renderQueue) {
            if (element.isVisible() && element.isPointInside(mouseX, mouseY)) {
                element.triggerClick(mouseX, mouseY, button);
                break;
            }
        }
    }

    public LayoutManager getLayoutManager() {
        return layoutManager;
    }

    public AnimationEngine getAnimationEngine() {
        return animationEngine;
    }

    public ThemeManager getThemeManager() {
        return themeManager;
    }

    public void cleanUp() {
        elements.clear();
        renderQueue.clear();
        animationEngine.cleanup();
        UIOverlayManager.getInstance().clear();
        jsContext = null;
        LOGGER.info("UIService cleaned up.");
    }

    public static class UIElement {
        protected final String type;
        protected final UIService service;
        protected String id;
        protected final Map<String, Object> properties = new ConcurrentHashMap<>();
        protected final Map<String, Value> eventHandlers = new ConcurrentHashMap<>();
        protected final List<UIElement> children = new CopyOnWriteArrayList<>();
        protected UIElement parent;
        protected boolean visible = true;
        protected boolean dirty = true;

        protected double x = 0, y = 0, width = 100, height = 30;
        protected String backgroundColor = "#FFFFFF";
        protected String textColor = "#000000";
        protected String text = "";
        protected int borderWidth = 0;
        protected String borderColor = "#000000";
        protected double opacity = 1.0;
        protected String position = "relative";
        protected String borderRadius = "0";
        protected String boxShadow = "none";
        protected String fontFamily = "minecraft";
        protected int fontSize = 12;
        protected String fontWeight = "normal";
        protected String textAlign = "left";
        protected String overflow = "visible";
        protected double paddingTop = 0, paddingRight = 0, paddingBottom = 0, paddingLeft = 0;
        protected double marginTop = 0, marginRight = 0, marginBottom = 0, marginLeft = 0;

        public UIElement(String type, UIService service) {
            this.type = type;
            this.service = service;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public UIElement setProperty(String key, Object value) {
            properties.put(key, value);
            markDirty();
            return this;
        }

        public Object getProperty(String key) {
            return properties.get(key);
        }

        public UIElement setText(String text) {
            this.text = text;
            markDirty();
            return this;
        }

        public String getText() {
            return text;
        }

        public UIElement setPosition(double x, double y) {
            this.x = x;
            this.y = y;
            markDirty();
            return this;
        }

        public UIElement setPositionMode(String mode) {
            this.position = mode;
            markDirty();
            return this;
        }

        public UIElement setSize(double width, double height) {
            this.width = width;
            this.height = height;
            markDirty();
            return this;
        }

        public UIElement setBackgroundColor(String color) {
            this.backgroundColor = color;
            markDirty();
            return this;
        }

        public UIElement setTextColor(String color) {
            this.textColor = color;
            markDirty();
            return this;
        }

        public UIElement setBorder(int width, String color) {
            this.borderWidth = width;
            this.borderColor = color;
            markDirty();
            return this;
        }

        public UIElement setBorderRadius(String radius) {
            this.borderRadius = radius;
            markDirty();
            return this;
        }

        public UIElement setBoxShadow(String shadow) {
            this.boxShadow = shadow;
            markDirty();
            return this;
        }

        public UIElement setFont(String family, int size, String weight) {
            this.fontFamily = family;
            this.fontSize = size;
            this.fontWeight = weight;
            markDirty();
            return this;
        }

        public UIElement setTextAlign(String align) {
            this.textAlign = align;
            markDirty();
            return this;
        }

        public UIElement setPadding(double top, double right, double bottom, double left) {
            this.paddingTop = top;
            this.paddingRight = right;
            this.paddingBottom = bottom;
            this.paddingLeft = left;
            markDirty();
            return this;
        }

        public UIElement setMargin(double top, double right, double bottom, double left) {
            this.marginTop = top;
            this.marginRight = right;
            this.marginBottom = bottom;
            this.marginLeft = left;
            markDirty();
            return this;
        }

        public UIElement setOpacity(double opacity) {
            this.opacity = Math.max(0.0, Math.min(1.0, opacity));
            markDirty();
            return this;
        }

        public UIElement onClick(Value callback) {
            if (callback != null && callback.canExecute()) {
                eventHandlers.put("click", callback);
            }
            return this;
        }

        public UIElement onHover(Value callback) {
            if (callback != null && callback.canExecute()) {
                eventHandlers.put("hover", callback);
            }
            return this;
        }

        public UIElement onFocus(Value callback) {
            if (callback != null && callback.canExecute()) {
                eventHandlers.put("focus", callback);
            }
            return this;
        }

        public UIElement onBlur(Value callback) {
            if (callback != null && callback.canExecute()) {
                eventHandlers.put("blur", callback);
            }
            return this;
        }

        public UIElement appendChild(UIElement child) {
            children.add(child);
            child.parent = this;
            markDirty();
            return this;
        }

        public UIElement removeChild(UIElement child) {
            children.remove(child);
            child.parent = null;
            markDirty();
            return this;
        }

        public UIElement show() {
            this.visible = true;
            service.addToRenderQueue(this);
            markDirty();
            return this;
        }

        public UIElement hide() {
            this.visible = false;
            service.removeFromRenderQueue(this);
            markDirty();
            return this;
        }

        public UIElement showAsOverlay() {
            service.addToRenderQueue(this);
            UIOverlayManager.getInstance().addOverlayElement(this);
            this.visible = true;
            return this;
        }

        public UIElement hideOverlay() {
            service.removeFromRenderQueue(this);
            UIOverlayManager.getInstance().removeOverlayElement(this);
            this.visible = false;
            return this;
        }

        public boolean isVisible() {
            return visible;
        }

        public boolean isPointInside(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        public void triggerClick(double mouseX, double mouseY, int button) {
            executeEventHandler("click", this, mouseX, mouseY, button);
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

        protected void executeEventHandler(String eventType, Object... args) {
            Value handler = eventHandlers.get(eventType);
            if (handler != null && service.scriptExecutor != null) {
                service.scriptExecutor.execute(() -> {
                    if (service.jsContext != null) {
                        try {
                            service.jsContext.enter();
                            handler.execute(args);
                        } catch (Exception e) {
                            LOGGER.error("Error executing {} handler", eventType, e);
                        } finally {
                            try {
                                service.jsContext.leave();
                            } catch (Exception e) {}
                        }
                    }
                });
            }
        }

        public UIElement animate(String property, Object from, Object to, int duration) {
            service.animationEngine.animate(this, property, from, to, duration);
            return this;
        }

        void markDirty() {
            this.dirty = true;
        }

        public boolean isDirty() {
            return dirty;
        }

        public void clearDirty() {
            this.dirty = false;
        }

        public String getType() { return type; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public String getBackgroundColor() { return backgroundColor; }
        public String getTextColor() { return textColor; }
        public int getBorderWidth() { return borderWidth; }
        public String getBorderColor() { return borderColor; }
        public String getBorderRadius() { return borderRadius; }
        public String getBoxShadow() { return boxShadow; }
        public double getOpacity() { return opacity; }
        public String getPositionMode() { return position; }
        public String getFontFamily() { return fontFamily; }
        public int getFontSize() { return fontSize; }
        public String getFontWeight() { return fontWeight; }
        public String getTextAlign() { return textAlign; }
        public double getPaddingTop() { return paddingTop; }
        public double getPaddingRight() { return paddingRight; }
        public double getPaddingBottom() { return paddingBottom; }
        public double getPaddingLeft() { return paddingLeft; }
        public double getMarginTop() { return marginTop; }
        public double getMarginRight() { return marginRight; }
        public double getMarginBottom() { return marginBottom; }
        public double getMarginLeft() { return marginLeft; }
        public List<UIElement> getChildren() { return new ArrayList<>(children); }
    }

    public static final class UIContainer extends UIElement {
        private String flexDirection = "row";
        private String justifyContent = "flex-start";
        private String alignItems = "stretch";
        private String flexWrap = "nowrap";
        private double gap = 0;

        public UIContainer(UIService service) {
            super("container", service);
        }

        public UIContainer setFlexDirection(String direction) {
            this.flexDirection = direction;
            markDirty();
            return this;
        }

        public UIContainer setJustifyContent(String justify) {
            this.justifyContent = justify;
            markDirty();
            return this;
        }

        public UIContainer setAlignItems(String align) {
            this.alignItems = align;
            markDirty();
            return this;
        }

        public UIContainer setFlexWrap(String wrap) {
            this.flexWrap = wrap;
            markDirty();
            return this;
        }

        public UIContainer setGap(double gap) {
            this.gap = gap;
            markDirty();
            return this;
        }

        public String getFlexDirection() { return flexDirection; }
        public String getJustifyContent() { return justifyContent; }
        public String getAlignItems() { return alignItems; }
        public String getFlexWrap() { return flexWrap; }
        public double getGap() { return gap; }
    }

    public static final class UIText extends UIElement {
        public UIText(String content, UIService service) {
            super("text", service);
            setText(content);
        }
    }

    public static final class UIButton extends UIElement {
        private boolean pressed = false;
        private boolean hovered = false;

        public UIButton(String text, UIService service) {
            super("button", service);
            setText(text);
            setBackgroundColor("#4A90E2");
            setTextColor("#FFFFFF");
            setBorder(1, "#357ABD");
            setBorderRadius("4px");
            setPadding(8, 16, 8, 16);
        }

        public boolean isPressed() { return pressed; }
        public boolean isHovered() { return hovered; }

        public void setPressed(boolean pressed) {
            this.pressed = pressed;
            markDirty();
        }

        public void setHovered(boolean hovered) {
            this.hovered = hovered;
            markDirty();
        }
    }

    public static final class UIInput extends UIElement {
        private String placeholder = "";
        private String value = "";
        private boolean focused = false;
        private int cursorPosition = 0;

        public UIInput(String placeholder, UIService service) {
            super("input", service);
            this.placeholder = placeholder;
            setBackgroundColor("#FFFFFF");
            setTextColor("#000000");
            setBorder(1, "#CCCCCC");
            setBorderRadius("4px");
            setPadding(8, 12, 8, 12);
        }

        public String getPlaceholder() { return placeholder; }
        public String getValue() { return value; }
        public boolean isFocused() { return focused; }
        public int getCursorPosition() { return cursorPosition; }

        public UIInput setValue(String value) {
            this.value = value;
            markDirty();
            return this;
        }

        public UIInput setFocused(boolean focused) {
            this.focused = focused;
            markDirty();
            return this;
        }

        public UIInput setCursorPosition(int position) {
            this.cursorPosition = Math.max(0, Math.min(value.length(), position));
            markDirty();
            return this;
        }
    }

    public static final class UIScreen extends Screen {
        private final UIService service;
        private final List<UIElement> elements = new CopyOnWriteArrayList<>();

        public UIScreen(String title, UIService service) {
            super(Text.literal(title));
            this.service = service;
        }

        public UIScreen addElement(UIElement element) {
            elements.add(element);
            return this;
        }

        public UIScreen removeElement(UIElement element) {
            elements.remove(element);
            return this;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            for (UIElement element : elements) {
                if (element.isVisible()) {
                    service.renderer.renderElement(context, element, mouseX, mouseY);
                }
            }
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (UIElement element : elements) {
                if (element.isVisible() && element.isPointInside(mouseX, mouseY)) {
                    element.triggerClick(mouseX, mouseY, button);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void mouseMoved(double mouseX, double mouseY) {
            for (UIElement element : elements) {
                if (element.isVisible() && element.isPointInside(mouseX, mouseY)) {
                    element.triggerHover(mouseX, mouseY);
                }
            }
            super.mouseMoved(mouseX, mouseY);
        }

        @Override
        public boolean shouldPause() {
            return true;
        }
    }

    private static final class UIRenderer {
        private final MinecraftClient client = MinecraftClient.getInstance();

        public void renderElement(DrawContext context, UIElement element, int mouseX, int mouseY) {
            if (!element.isVisible()) return;

            int x = (int) element.getX();
            int y = (int) element.getY();
            int width = (int) element.getWidth();
            int height = (int) element.getHeight();

            int bgColor = parseColor(element.getBackgroundColor(), element.getOpacity());
            int textColor = parseColor(element.getTextColor(), 1.0);
            int borderColor = parseColor(element.getBorderColor(), 1.0);

            context.fill(x, y, x + width, y + height, bgColor);

            if (element.getBorderWidth() > 0) {
                int bw = element.getBorderWidth();
                context.fill(x, y, x + width, y + bw, borderColor);
                context.fill(x, y + height - bw, x + width, y + height, borderColor);
                context.fill(x, y, x + bw, y + height, borderColor);
                context.fill(x + width - bw, y, x + width, y + height, borderColor);
            }

            if (!element.getText().isEmpty()) {
                int textX = x + (int)element.getPaddingLeft() + 5;
                int textY = y + (height - 8) / 2;

                String displayText = element.getText();
                if (element instanceof UIInput input && input.isFocused()) {
                    displayText = input.getValue().isEmpty() ? input.getPlaceholder() : input.getValue();
                }

                context.drawText(client.textRenderer, displayText, textX, textY, textColor, false);
            }

            for (UIElement child : element.getChildren()) {
                renderElement(context, child, mouseX, mouseY);
            }
        }

        private int parseColor(String color, double opacity) {
            int alpha = (int) (opacity * 255) << 24;
            if (color.startsWith("#")) {
                return Integer.parseInt(color.substring(1), 16) | alpha;
            }
            return 0xFFFFFF | alpha;
        }
    }
}