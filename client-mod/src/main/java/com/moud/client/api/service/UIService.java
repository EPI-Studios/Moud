package com.moud.client.api.service;

import com.moud.client.ui.UIOverlayManager;
import com.moud.client.ui.component.*;
import com.moud.client.network.ClientNetworkManager;
import com.moud.network.MoudPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.UUID;

public final class UIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIService.class);
    private final MinecraftClient client;
    private final UIOverlayManager overlayManager;
    private final Map<String, UIComponent> elements = new ConcurrentHashMap<>();

    private Context jsContext;
    private ExecutorService scriptExecutor;
    private Value resizeCallback = null;
    private volatile int pendingResizeWidth = -1;
    private volatile int pendingResizeHeight = -1;

    public UIService() {
        this.client = MinecraftClient.getInstance();
        this.overlayManager = UIOverlayManager.getInstance();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
        flushPendingResize();
    }

    public void setExecutor(ExecutorService executor) {
        this.scriptExecutor = executor;
        flushPendingResize();
    }

    public Context getJsContext() {
        return jsContext;
    }

    public ExecutorService getScriptExecutor() {
        return scriptExecutor;
    }

    @HostAccess.Export
    public int getScreenWidth() {
        return client.getWindow().getScaledWidth();
    }

    @HostAccess.Export
    public int getScreenHeight() {
        return client.getWindow().getScaledHeight();
    }

    @HostAccess.Export
    public int getMouseX() {
        return (int) (client.mouse.getX() * getScreenWidth() / client.getWindow().getWidth());
    }

    @HostAccess.Export
    public int getMouseY() {
        return (int) (client.mouse.getY() * getScreenHeight() / client.getWindow().getHeight());
    }

    @HostAccess.Export
    public int getTextWidth(String text) {
        if (text == null) return 0;
        return client.textRenderer.getWidth(text);
    }

    public <T extends UIComponent> T registerComponent(T component, boolean addToOverlay) {
        String id = component.getComponentId();
        if (id == null || id.isBlank()) {
            id = "moud_ui_" + UUID.randomUUID();
            component.setComponentId(id);
        }
        elements.put(id, component);
        if (addToOverlay) {
            overlayManager.addOverlayElement(component);
        }
        return component;
    }

    private <T extends UIComponent> T registerComponent(T component) {
        return registerComponent(component, true);
    }

    @HostAccess.Export
    public UIText createText(String content) {
        UIText text = new UIText(content, this);
        text.setSize(client.textRenderer.getWidth(content), client.textRenderer.fontHeight);
        return registerComponent(text);
    }

    @HostAccess.Export
    public UIButton createButton(String text) {
        UIButton button = new UIButton(text, this);
        button.setSize(Math.max(80, client.textRenderer.getWidth(text) + 20), 20);
        return registerComponent(button);
    }

    @HostAccess.Export
    public UIInput createInput(String placeholder) {
        UIInput input = new UIInput(placeholder, this);
        return registerComponent(input);
    }

    @HostAccess.Export
    public UIContainer createContainer() {
        UIContainer container = new UIContainer(this);
        return registerComponent(container);
    }

    public void removeElement(String id) {
        UIComponent component = elements.remove(id);
        if (component != null) {
            if (component.parent != null) {
                component.parent.removeChild(component);
            }
            overlayManager.removeOverlayElement(component);
        }
    }

    public UIComponent getElement(String id) {
        return elements.get(id);
    }

    @HostAccess.Export
    public void onResize(Value callback) {
        if (callback != null && callback.canExecute()) {
            this.resizeCallback = callback;
            LOGGER.info("UI resize callback registered.");
        }
    }

    @HostAccess.Export
    public UIImage createImage(String source) {
        UIImage image = new UIImage(source, this);
        image.setSize(64, 64);
        return registerComponent(image);
    }

    public void notifyServerInteraction(UIComponent component, String action, Map<String, Object> payload) {
        if (component == null || !component.isServerControlled()) {
            return;
        }
        try {
            ClientNetworkManager.send(new MoudPackets.UIInteractionPacket(
                    component.getComponentId(),
                    action,
                    payload
            ));
        } catch (Exception e) {
            LOGGER.debug("Failed to send UI interaction for component {}", component.getComponentId(), e);
        }
    }


    public void triggerResizeEvent() {
        int w = getScreenWidth();
        int h = getScreenHeight();
        if (resizeCallback != null && scriptExecutor != null && !scriptExecutor.isShutdown() && jsContext != null) {
            scriptExecutor.execute(() -> {
                jsContext.enter();
                try {
                    LOGGER.debug("Firing UI resize event to script.");
                    resizeCallback.execute(w, h);
                } catch (Exception e) {
                    LOGGER.error("Error executing UI resize callback", e);
                } finally {
                    jsContext.leave();
                }
            });
        } else {
            pendingResizeWidth = w;
            pendingResizeHeight = h;
        }
    }

    private void flushPendingResize() {
        if (resizeCallback == null || jsContext == null || scriptExecutor == null || scriptExecutor.isShutdown()) {
            return;
        }
        if (pendingResizeWidth < 0 || pendingResizeHeight < 0) {
            return;
        }
        int w = pendingResizeWidth;
        int h = pendingResizeHeight;
        pendingResizeWidth = pendingResizeHeight = -1;
        triggerResizeEventWithArgs(w, h);
    }

    private void triggerResizeEventWithArgs(int width, int height) {
        if (resizeCallback == null || jsContext == null || scriptExecutor == null || scriptExecutor.isShutdown()) {
            pendingResizeWidth = width;
            pendingResizeHeight = height;
            return;
        }
        scriptExecutor.execute(() -> {
            jsContext.enter();
            try {
                LOGGER.debug("Firing deferred UI resize event to script.");
                resizeCallback.execute(width, height);
            } catch (Exception e) {
                LOGGER.error("Error executing deferred UI resize callback", e);
            } finally {
                jsContext.leave();
            }
        });
    }


    public void cleanUp() {
        elements.values().forEach(overlayManager::removeOverlayElement);
        elements.clear();
        jsContext = null;
        scriptExecutor = null;
        LOGGER.info("UIService cleaned up.");
    }

    public void showToast(String title, String body) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
    }
}
