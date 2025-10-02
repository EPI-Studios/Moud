// File: src/main/java/com/moud/client/ui/UIInputManager.java

package com.moud.client.ui;

import com.moud.client.ui.component.UIComponent;
import com.moud.client.ui.component.UIInput;
import org.lwjgl.glfw.GLFW;

public final class UIInputManager {

    private UIInputManager() {}

    public static boolean handleGlobalKeyPress(int key, int action) {
        UIComponent focused = UIFocusManager.getFocusedComponent();
        if (focused instanceof UIInput input) {

            if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
                if (input.handleKeyPressed(key)) {
                    return true;
                }
            }
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                return true;
            }
        }
        return false;
    }

    public static boolean handleGlobalCharTyped(char c) {
        UIComponent focused = UIFocusManager.getFocusedComponent();
        if (focused instanceof UIInput input) {
            input.handleCharTyped(c);
            return true;
        }
        return false;
    }
}