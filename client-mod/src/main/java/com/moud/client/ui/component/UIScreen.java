package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;
import com.moud.client.ui.UIFocusManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UIScreen extends Screen {
    private final UIService service;
    private final List<UIComponent> elements = new CopyOnWriteArrayList<>();

    public UIScreen(String title, UIService service) {
        super(Text.literal(title));
        this.service = service;
    }

    public UIScreen addElement(UIComponent element) {
        elements.add(element);
        addDrawableChild(element);
        return this;
    }

    public UIScreen removeElement(UIComponent element) {
        elements.remove(element);
        remove(element);
        return this;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    protected void init() {
        super.init();
        for (UIComponent element : elements) {
            addDrawableChild(element);
        }
        repositionElements();
    }

    @Override
    public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        repositionElements();
    }

    private void repositionElements() {
        for (UIComponent element : elements) {
            if (element.parent == null) {
                updateElementLayout(element);
            }
        }
    }

    private void updateElementLayout(UIComponent element) {
        if (element instanceof UIContainer container) {
            container.updateLayout();
        }

        for (UIComponent child : element.getChildren()) {
            updateElementLayout(child);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            UIComponent element = elements.get(i);
            if (element.visible && element.mouseClicked(mouseX, mouseY, button)) {
                setFocused(element);
                return true;
            }
        }

        setFocused(null);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        UIFocusManager.clearFocus();
        super.close();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (getFocused() instanceof UIInput input) {
            input.handleCharTyped(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof UIInput input) {
            input.handleKeyPressed(keyCode);
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}