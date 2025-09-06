package com.moud.client.api.service;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.graalvm.polyglot.Value;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InputService {
    private final MinecraftClient client;
    private final Map<String, Value> keyCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Value> mouseCallbacks = new ConcurrentHashMap<>();
    private Value mouseMoveCallback;
    private Value scrollCallback;

    private double lastMouseX, lastMouseY;
    private double mouseDeltaX, mouseDeltaY;

    private boolean isCapturingMouse = false;

    public InputService() {
        this.client = MinecraftClient.getInstance();
    }

    public boolean isKeyPressed(int keyCode) {
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
    }

    public boolean isKeyPressed(String keyName) {
        try {
            InputUtil.Key key = InputUtil.fromTranslationKey(keyName);
            return isKeyPressed(key.getCode());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isMouseButtonPressed(int button) {
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }

    public double getMouseX() {
        return client.mouse.getX();
    }

    public double getMouseY() {
        return client.mouse.getY();
    }

    public double getMouseDeltaX() {
        return mouseDeltaX;
    }

    public double getMouseDeltaY() {
        return mouseDeltaY;
    }

    public void updateMouseDelta() {
        double currentX = getMouseX();
        double currentY = getMouseY();

        mouseDeltaX = currentX - lastMouseX;
        mouseDeltaY = currentY - lastMouseY;

        lastMouseX = currentX;
        lastMouseY = currentY;
    }

    public void onKey(String keyName, Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
        }
        keyCallbacks.put(keyName, callback);
    }

    public void onMouseButton(String buttonName, Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
        }
        mouseCallbacks.put(buttonName, callback);
    }

    public void onMouseMove(Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
        }
        this.mouseMoveCallback = callback;
    }

    public void onScroll(Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
        }
        this.scrollCallback = callback;
    }

    public void triggerKeyEvent(String keyName, boolean pressed) {
        Value callback = keyCallbacks.get(keyName);
        if (callback != null && callback.canExecute()) {
            callback.execute(keyName, pressed);
        }
    }

    public void triggerMouseButtonEvent(String buttonName, boolean pressed) {
        Value callback = mouseCallbacks.get(buttonName);
        if (callback != null && callback.canExecute()) {
            callback.execute(buttonName, pressed);
        }
    }

    public void triggerMouseMoveEvent(double deltaX, double deltaY) {
        if (mouseMoveCallback != null && mouseMoveCallback.canExecute()) {
            mouseMoveCallback.execute(deltaX, deltaY);
        }
    }

    public void triggerScrollEvent(double scrollDelta) {
        if (scrollCallback != null && scrollCallback.canExecute()) {
            scrollCallback.execute(scrollDelta);
        }
    }

    public void lockMouse(boolean locked) {
        if (locked) {
            GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        } else {
            GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    public boolean isMouseLocked() {
        return GLFW.glfwGetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED;
    }

    public double getMouseSensitivity() {
        return client.options.getMouseSensitivity().getValue();
    }

    public void setMouseSensitivity(float sensitivity) {
        client.options.getMouseSensitivity().setValue((double) sensitivity);
    }

    public void update() {

        if (isCapturingMouse && mouseMoveCallback != null) {
            double currentX = client.mouse.getX();
            double currentY = client.mouse.getY();

            mouseDeltaX = currentX - lastMouseX;
            mouseDeltaY = currentY - lastMouseY;

            if (mouseDeltaX != 0 || mouseDeltaY != 0) {
                mouseMoveCallback.execute(mouseDeltaX, mouseDeltaY);
            }

            lastMouseX = currentX;
            lastMouseY = currentY;
        }
    }
}