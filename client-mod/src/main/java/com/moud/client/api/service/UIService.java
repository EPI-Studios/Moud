package com.moud.client.api.service;

import com.moud.client.ui.UIOverlayManager;
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
        for (UIElement element : renderQueue) {
            if (element.isVisible()) {
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

    public void cleanUp() {
        elements.clear();
        renderQueue.clear();
        UIOverlayManager.getInstance().clear();
        jsContext = null;
        LOGGER.info("UIService cleaned up.");
    }

    public static final class UIElement {
        private final String type;
        private final UIService service;
        private String id;
        private final Map<String, Object> properties = new ConcurrentHashMap<>();
        private final Map<String, Value> eventHandlers = new ConcurrentHashMap<>();
        private final List<UIElement> children = new CopyOnWriteArrayList<>();
        private UIElement parent;
        private boolean visible = true;
        private boolean dirty = true;

        private double x = 0, y = 0, width = 100, height = 30;
        private String backgroundColor = "#FFFFFF";
        private String textColor = "#000000";
        private String text = "";
        private int borderWidth = 0;
        private String borderColor = "#000000";
        private double opacity = 1.0;
        private String position = "relative";

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
            Value handler = eventHandlers.get("click");
            if (handler != null && service.scriptExecutor != null) {
                service.scriptExecutor.execute(() -> {
                    if (service.jsContext != null) {
                        try {
                            service.jsContext.enter();
                            handler.execute(this, mouseX, mouseY, button);
                        } catch (Exception e) {
                            LOGGER.error("Error executing click handler", e);
                        } finally {
                            try {
                                service.jsContext.leave();
                            } catch (Exception e) {}
                        }
                    }
                });
            }
        }

        public void triggerHover(double mouseX, double mouseY) {
            Value handler = eventHandlers.get("hover");
            if (handler != null && service.scriptExecutor != null) {
                service.scriptExecutor.execute(() -> {
                    if (service.jsContext != null) {
                        try {
                            service.jsContext.enter();
                            handler.execute(this, mouseX, mouseY);
                        } catch (Exception e) {
                            LOGGER.error("Error executing hover handler", e);
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
            return this;
        }

        private void markDirty() {
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
        public double getOpacity() { return opacity; }
        public String getPositionMode() { return position; }
        public List<UIElement> getChildren() { return new ArrayList<>(children); }
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
                int textX = x + 5;
                int textY = y + (height - 8) / 2;
                context.drawText(client.textRenderer, element.getText(), textX, textY, textColor, false);
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