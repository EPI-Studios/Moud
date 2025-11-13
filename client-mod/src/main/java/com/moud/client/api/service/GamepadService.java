package com.moud.client.api.service;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GamepadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GamepadService.class);
    private static final int MAX_GAMEPADS = 4;

    private final GamepadSnapshot[] snapshots = new GamepadSnapshot[MAX_GAMEPADS];
    private final Map<String, Value> listeners = new ConcurrentHashMap<>();
    private Context context;
    private volatile boolean vibrationEnabled = true;

    public void setContext(Context context) {
        this.context = context;
    }

    public void cleanUp() {
        listeners.clear();
        context = null;
        Arrays.fill(snapshots, null);
    }

    public void tick() {
        for (int index = 0; index < MAX_GAMEPADS; index++) {
            boolean present = GLFW.glfwJoystickIsGamepad(index);
            if (!present) {
                if (snapshots[index] != null) {
                    snapshots[index] = null;
                    fireEvent(GamepadSnapshot.disconnected(index));
                }
                continue;
            }

            GLFWGamepadState state = GLFWGamepadState.create();
            if (!GLFW.glfwGetGamepadState(index, state)) {
                continue;
            }

            GamepadSnapshot snapshot = GamepadSnapshot.from(index, state, System.currentTimeMillis());
            GamepadSnapshot previous = snapshots[index];
            snapshots[index] = snapshot;

            if (!snapshot.equals(previous)) {
                fireEvent(snapshot);
            }
        }
    }

    @HostAccess.Export
    public boolean isVibrationEnabled() {
        return vibrationEnabled;
    }

    @HostAccess.Export
    public void setVibrationEnabled(boolean enabled) {
        this.vibrationEnabled = enabled;
    }

    private void fireEvent(GamepadSnapshot snapshot) {
        if (listeners.isEmpty() || context == null) {
            return;
        }

        try {
            context.enter();
            for (Value listener : listeners.values()) {
                if (listener == null || !listener.canExecute()) {
                    continue;
                }
                try {
                    listener.execute(snapshot);
                } catch (Exception e) {
                    LOGGER.error("Gamepad listener execution failed", e);
                }
            }
        } finally {
            context.leave();
        }
    }

    @HostAccess.Export
    public boolean isConnected(int index) {
        validateIndex(index);
        return snapshots[index] != null && snapshots[index].connected;
    }

    @HostAccess.Export
    public GamepadSnapshot getState(int index) {
        validateIndex(index);
        return snapshots[index];
    }

    @HostAccess.Export
    public String onChange(Value callback) {
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("Callback must be a function");
        }
        String id = "gamepad_listener_" + System.nanoTime();
        listeners.put(id, callback);
        return id;
    }

    @HostAccess.Export
    public void removeListener(String id) {
        if (id != null) {
            listeners.remove(id);
        }
    }

    private void validateIndex(int index) {
        if (index < 0 || index >= MAX_GAMEPADS) {
            throw new IllegalArgumentException("Gamepad index out of range: " + index);
        }
    }

    public static final class GamepadSnapshot {
        private final int index;
        private final String name;
        private final float[] axes;
        private final boolean[] buttons;
        private final boolean connected;
        private final long timestamp;

        private GamepadSnapshot(int index, String name, float[] axes, boolean[] buttons, boolean connected, long timestamp) {
            this.index = index;
            this.name = name;
            this.axes = axes;
            this.buttons = buttons;
            this.connected = connected;
            this.timestamp = timestamp;
        }

        public static GamepadSnapshot from(int index, GLFWGamepadState state, long now) {
            float[] axes = new float[GLFW.GLFW_GAMEPAD_AXIS_LAST + 1];
            for (int i = 0; i < axes.length; i++) {
                axes[i] = state.axes(i);
            }

            boolean[] buttons = new boolean[GLFW.GLFW_GAMEPAD_BUTTON_LAST + 1];
            for (int i = 0; i < buttons.length; i++) {
                buttons[i] = state.buttons(i) == GLFW.GLFW_PRESS;
            }

            String name = GLFW.glfwGetGamepadName(index);
            return new GamepadSnapshot(index, name, axes, buttons, true, now);
        }

        public static GamepadSnapshot disconnected(int index) {
            return new GamepadSnapshot(index, null, new float[0], new boolean[0], false, System.currentTimeMillis());
        }

        @HostAccess.Export
        public int getIndex() {
            return index;
        }

        @HostAccess.Export
        public String getName() {
            return name;
        }

        @HostAccess.Export
        public float[] getAxes() {
            return axes;
        }

        @HostAccess.Export
        public boolean[] getButtons() {
            return buttons;
        }

        @HostAccess.Export
        public boolean isConnected() {
            return connected;
        }

        @HostAccess.Export
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof GamepadSnapshot other)) return false;
            return index == other.index
                    && connected == other.connected
                    && Arrays.equals(axes, other.axes)
                    && Arrays.equals(buttons, other.buttons);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(axes);
            result = 31 * result + Arrays.hashCode(buttons);
            result = 31 * result + Boolean.hashCode(connected);
            result = 31 * result + index;
            return result;
        }
    }
}
