package com.moud.client.api.service;

import com.moud.client.camera.CameraManager;
import com.moud.client.runtime.ClientScriptingRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public final class InputService {
    private final MinecraftClient client;
    private final Map<String, Value> keyCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Value> mouseCallbacks = new ConcurrentHashMap<>();
    private Value mouseMoveCallback;
    private Value scrollCallback;

    private double lastMouseX, lastMouseY;
    private double mouseDeltaX, mouseDeltaY;

    private static final Logger LOGGER = LoggerFactory.getLogger(InputService.class);
    private Context jsContext;
    private final ExecutorService scriptExecutor;

    private final Map<String, Boolean> keyStates = new ConcurrentHashMap<>();
    private final Map<String, Long> lastKeyTriggerTime = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_TIME_MS = 100;

    public InputService(ClientScriptingRuntime runtime) {
        this.client = MinecraftClient.getInstance();
        this.scriptExecutor = runtime.getExecutor();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
        LOGGER.debug("InputService received new GraalVM Context.");
    }

    public boolean handleKeyEvent(int key, int action) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return false;

        String keyName = InputUtil.fromKeyCode(key, -1).getTranslationKey();
        boolean isPressed = (action == GLFW.GLFW_PRESS);

        boolean wasPressed = keyStates.getOrDefault(keyName, false);

        if (isPressed == wasPressed) {
            return false;
        }

        keyStates.put(keyName, isPressed);

        boolean hasCallback = keyCallbacks.containsKey(keyName);
        if (hasCallback) {
            triggerKeyEvent(keyName, isPressed);
        }

        return hasCallback;
    }

    @HostAccess.Export
    public boolean isKeyPressed(int keyCode) {
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
    }

    @HostAccess.Export
    public boolean isKeyPressed(String keyName) {
        try {
            InputUtil.Key key = InputUtil.fromTranslationKey(keyName);
            return isKeyPressed(key.getCode());
        } catch (Exception e) {
            return false;
        }
    }

    @HostAccess.Export
    public boolean isMouseButtonPressed(int button) {
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }

    @HostAccess.Export
    public double getMouseX() {
        return client.mouse.getX();
    }

    @HostAccess.Export
    public double getMouseY() {
        return client.mouse.getY();
    }

    @HostAccess.Export
    public double getMouseDeltaX() {
        return mouseDeltaX;
    }

    @HostAccess.Export
    public double getMouseDeltaY() {
        return mouseDeltaY;
    }

    @HostAccess.Export
    public void onKey(String keyName, Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
        }
        keyCallbacks.put(keyName, callback);
        LOGGER.info("Successfully registered key callback for: {}", keyName);
    }

    @HostAccess.Export
    public void onMouseButton(String buttonName, Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
        }
        mouseCallbacks.put(buttonName, callback);
    }

    @HostAccess.Export
    public void onMouseMove(Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
        }
        this.mouseMoveCallback = callback;
    }

    @HostAccess.Export
    public void onScroll(Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be executable");
        }
        this.scrollCallback = callback;
    }

    @HostAccess.Export
    public boolean isMovingForward() {
        return client.options.forwardKey.isPressed();
    }

    @HostAccess.Export
    public boolean isMovingBackward() {
        return client.options.backKey.isPressed();
    }

    @HostAccess.Export
    public boolean isStrafingLeft() {
        return client.options.leftKey.isPressed();
    }

    @HostAccess.Export
    public boolean isStrafingRight() {
        return client.options.rightKey.isPressed();
    }

    @HostAccess.Export
    public boolean isJumping() {
        return client.options.jumpKey.isPressed();
    }

    @HostAccess.Export
    public boolean isSprinting() {
        return client.player != null && client.player.isSprinting();
    }

    @HostAccess.Export
    public boolean isOnGround() {
        return client.player != null && client.player.isOnGround();
    }

    @HostAccess.Export
    public boolean isMoving() {
        return isMovingForward() || isMovingBackward() || isStrafingLeft() || isStrafingRight();
    }
    public void triggerKeyEvent(String keyName, boolean pressed) {
        Value callback = keyCallbacks.get(keyName);
        if (callback != null) {
            long currentTime = System.currentTimeMillis();
            Long lastTrigger = lastKeyTriggerTime.get(keyName);

            if (lastTrigger != null && (currentTime - lastTrigger) < DEBOUNCE_TIME_MS) {
                return;
            }

            lastKeyTriggerTime.put(keyName, currentTime);

            scriptExecutor.execute(() -> {
                if (jsContext == null) {
                    LOGGER.warn("jsContext is null, cannot execute key event for {}", keyName);
                    return;
                }
                jsContext.enter();
                try {
                    if (callback.canExecute()) {
                        callback.execute(pressed);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error executing script key event callback for '{}'", keyName, e);
                } finally {
                    jsContext.leave();
                }
            });
        }
    }

    public void triggerMouseButtonEvent(String buttonName, boolean pressed) {
        Value callback = mouseCallbacks.get(buttonName);
        if (callback != null) {
            executeCallback(callback, buttonName, pressed);
        }
    }

    public void triggerMouseMoveEvent(double deltaX, double deltaY) {
        if (CameraManager.isCameraActive()) {
            CameraManager.handleInput(deltaX, deltaY, 0);
        }
        if (mouseMoveCallback != null) {
            executeCallback(mouseMoveCallback, deltaX, deltaY);
        }
    }

    public boolean triggerScrollEvent(double scrollDelta) {
        if (scrollCallback != null) {
            executeCallback(scrollCallback, scrollDelta);
            return true;
        }
        return false;
    }




    private void executeCallback(Value callback, Object... args) {
        if (scriptExecutor == null || scriptExecutor.isShutdown() || jsContext == null) {
            return;
        }

        scriptExecutor.execute(() -> {
            jsContext.enter();
            try {
                if (callback.canExecute()) {
                    callback.execute(args);
                }
            } catch (Exception e) {
                LOGGER.error("Error executing script callback", e);
            } finally {
                jsContext.leave();
            }
        });
    }

    @HostAccess.Export
    public void lockMouse(boolean locked) {
        GLFW.glfwSetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR, locked ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
    }

    @HostAccess.Export
    public boolean isMouseLocked() {
        return GLFW.glfwGetInputMode(client.getWindow().getHandle(), GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED;
    }

    @HostAccess.Export
    public double getMouseSensitivity() {
        return client.options.getMouseSensitivity().getValue();
    }

    @HostAccess.Export
    public void setMouseSensitivity(float sensitivity) {
        client.options.getMouseSensitivity().setValue((double) sensitivity);
    }

    public void update() {
    }

    public void cleanUp() {
        keyCallbacks.clear();
        mouseCallbacks.clear();
        mouseMoveCallback = null;
        scrollCallback = null;
        jsContext = null;
        keyStates.clear();
        lastKeyTriggerTime.clear();
        LOGGER.info("InputService cleaned up.");
    }
}