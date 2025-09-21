package com.moud.client.ui;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class UIInputHandler {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private UIElement focusedElement = null;

    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean handleKeyPress(int key, int action) {
        if (focusedElement == null || !"input".equals(focusedElement.getType())) {
            return false;
        }

        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            String currentValue = focusedElement.getValue();

            switch (key) {
                case GLFW.GLFW_KEY_BACKSPACE:
                    if (!currentValue.isEmpty()) {
                        focusedElement.setValue(currentValue.substring(0, currentValue.length() - 1));
                    }
                    break;
                case GLFW.GLFW_KEY_ENTER:
                    setFocus(null);
                    break;
                case GLFW.GLFW_KEY_ESCAPE:
                    setFocus(null);
                    break;
            }
        }

        return true;
    }

    public boolean handleCharTyped(char character) {
        if (focusedElement == null || !"input".equals(focusedElement.getType())) {
            return false;
        }

        if (isValidCharacter(character)) {
            String currentValue = focusedElement.getValue();
            focusedElement.setValue(currentValue + character);
        }

        return true;
    }

    public void setFocus(UIElement element) {
        this.focusedElement = element;
    }

    public UIElement getFocusedElement() {
        return focusedElement;
    }

    private boolean isValidCharacter(char character) {
        return character >= 32 && character != 127;
    }
}