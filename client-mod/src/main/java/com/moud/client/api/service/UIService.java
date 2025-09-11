package com.moud.client.api.service;

import com.moud.client.ui.UIOverlayManager;
import com.moud.client.ui.UIRenderer;
import com.moud.client.ui.animation.AnimationEngine;
import com.moud.client.ui.component.*;
import com.moud.client.ui.layout.LayoutEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.graalvm.polyglot.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

public final class UIService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIService.class);
    private final MinecraftClient client;
    private final LayoutEngine layoutEngine = new LayoutEngine();
    private final AnimationEngine animationEngine = new AnimationEngine();
    private final UIRenderer renderer = new UIRenderer();

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

    public void render(DrawContext context, int mouseX, int mouseY, float delta, List<UIComponent> elementsToRender) {
        animationEngine.update(delta);
        for (UIComponent element : elementsToRender) {
            if (element.isVisible()) {
                layoutEngine.updateLayout(element);
                renderer.renderComponent(context, element);
            }
        }
    }

    public int getScreenWidth() {
        return this.client.getWindow().getScaledWidth();
    }

    public int getScreenHeight() {
        return this.client.getWindow().getScaledHeight();
    }

    public UIComponent createElement(String type) {
        UIComponent component = new UIComponent(type, this);
        component.setId(UUID.randomUUID().toString());
        return component;
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

    public void cleanUp() {
        animationEngine.cleanup();
        UIOverlayManager.getInstance().clear();
        jsContext = null;
        scriptExecutor = null;
        LOGGER.info("UIService cleaned up.");
    }

    public static final class UIScreen extends Screen {
        private final UIService service;
        private final List<UIComponent> elements = new CopyOnWriteArrayList<>();

        public UIScreen(String title, UIService service) {
            super(Text.literal(title));
            this.service = service;
        }

        public UIScreen addElement(UIComponent element) {
            elements.add(element);
            return this;
        }

        public UIScreen removeElement(UIComponent element) {
            elements.remove(element);
            return this;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.renderBackground(context, mouseX, mouseY, delta);
            service.render(context, mouseX, mouseY, delta, this.elements);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean elementClicked = false;
            for (int i = elements.size() - 1; i >= 0; i--) {
                UIComponent element = elements.get(i);
                if (element.isVisible() && element.isPointInside(mouseX, mouseY)) {
                    element.triggerClick(mouseX, mouseY, button);
                    com.moud.client.ui.UIFocusManager.setFocus(element);
                    elementClicked = true;
                    break;
                }
            }
            if (!elementClicked) {
                com.moud.client.ui.UIFocusManager.clearFocus();
            }
            return elementClicked || super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void close() {
            com.moud.client.ui.UIFocusManager.clearFocus();
            this.client.setScreen(null);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            UIComponent focused = com.moud.client.ui.UIFocusManager.getFocusedComponent();
            if (focused instanceof UIInput input) {
                input.handleCharTyped(chr);
                return true;
            }
            return super.charTyped(chr, modifiers);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            UIComponent focused = com.moud.client.ui.UIFocusManager.getFocusedComponent();
            if (focused instanceof UIInput input) {
                input.handleKeyPressed(keyCode);

                if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}