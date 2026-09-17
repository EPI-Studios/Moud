package com.meekdev.moud.mod.client.input;

import com.google.gson.JsonElement;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.client.JsonResources;
import com.meekdev.moud.script.api.DevicesRef;
import com.meekdev.moud.script.host.Host;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

public final class Gamepads {

    private static final String NAME_TABLE = "/assets/moud/input/gamepad.json";

    public static final List<String> NAMES = names();

    private static final int BUTTONS = GLFW.GLFW_GAMEPAD_BUTTON_LAST + 1;
    private static final int TRIGGER_L = BUTTONS;
    private static final int STICK_L = BUTTONS + 2;
    private static final double DEAD_ZONE = 0.1;
    private static final double TRIGGER_DOWN = 0.1;
    private static final double STICK_DOWN = 0.5;
    private static final double STEP = 0.01;

    private static final class Pad {
        boolean connected;
        final boolean[] buttons = new boolean[BUTTONS];
        final double[] triggers = new double[2];
        final Vector3[] sticks = {Vector3.ZERO, Vector3.ZERO};
    }

    private static final Pad[] PADS = new Pad[GLFW.GLFW_JOYSTICK_LAST + 1];
    private static final GLFWGamepadState STATE = GLFWGamepadState.create();

    static {
        for (int n = 0; n < PADS.length; n++) PADS[n] = new Pad();
    }

    private Gamepads() {}

    private static List<String> names() {
        List<String> names = new ArrayList<>();
        for (JsonElement name : JsonResources.read(NAME_TABLE).getAsJsonArray("inputs")) names.add(name.getAsString());
        return List.copyOf(names);
    }

    public static void frame(@Nullable Host host, boolean processed) {
        for (int jid = 0; jid < PADS.length; jid++) {
            Pad pad = PADS[jid];
            boolean present = GLFW.glfwJoystickIsGamepad(jid) && GLFW.glfwGetGamepadState(jid, STATE);
            if (!present) {
                if (pad.connected) disconnect(host, jid, pad, processed);
                continue;
            }
            if (!pad.connected) {
                pad.connected = true;
                if (host != null) host.gamepad(jid, true);
            }
            for (int button = 0; button < BUTTONS; button++) {
                boolean down = STATE.buttons(button) == GLFW.GLFW_PRESS;
                if (down == pad.buttons[button]) continue;
                pad.buttons[button] = down;
                send(host, button, edge(down), Vector3.ZERO, Vector3.ZERO, processed);
            }
            for (int side = 0; side < 2; side++) {
                double value = (STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER + side) + 1) / 2;
                double was = pad.triggers[side];
                boolean down = value > TRIGGER_DOWN;
                boolean wasDown = was > TRIGGER_DOWN;
                if (down == wasDown && (!down || Math.abs(value - was) < STEP)) continue;
                pad.triggers[side] = value;
                String state = down == wasDown ? "change" : edge(down);
                send(host, TRIGGER_L + side, state, new Vector3(0, 0, value), new Vector3(0, 0, value - was), processed);
            }
            for (int side = 0; side < 2; side++) {
                Vector3 stick = new Vector3(STATE.axes(side * 2), -STATE.axes(side * 2 + 1), 0);
                if (stick.length() < DEAD_ZONE) stick = Vector3.ZERO;
                Vector3 was = pad.sticks[side];
                if (stick.equals(was) || stick.sub(was).length() < STEP && !stick.equals(Vector3.ZERO)) continue;
                pad.sticks[side] = stick;
                send(host, STICK_L + side, "change", stick, stick.sub(was), processed);
            }
        }
    }

    private static void disconnect(@Nullable Host host, int jid, Pad pad, boolean processed) {
        for (int button = 0; button < BUTTONS; button++) {
            if (pad.buttons[button]) send(host, button, "end", Vector3.ZERO, Vector3.ZERO, processed);
        }
        for (int side = 0; side < 2; side++) {
            if (pad.triggers[side] > TRIGGER_DOWN) send(host, TRIGGER_L + side, "end", Vector3.ZERO, Vector3.ZERO, processed);
        }
        PADS[jid] = new Pad();
        if (host != null) host.gamepad(jid, false);
    }

    private static String edge(boolean down) {
        return down ? "begin" : "end";
    }

    private static void send(@Nullable Host host, int index, String state, Vector3 position, Vector3 delta, boolean processed) {
        if (host != null) host.input(new DevicesRef.Event("gamepad", NAMES.get(index), state, position, delta, processed));
    }

    public static boolean down(int index) {
        for (Pad pad : PADS) {
            if (!pad.connected) continue;
            if (index < BUTTONS && pad.buttons[index]) return true;
            if (index >= TRIGGER_L && index < STICK_L && pad.triggers[index - TRIGGER_L] > TRIGGER_DOWN) return true;
            if (index >= STICK_L && index < NAMES.size() && pad.sticks[index - STICK_L].length() > STICK_DOWN) return true;
        }
        return false;
    }

    public static List<DevicesRef.Gamepad> all() {
        List<DevicesRef.Gamepad> out = new ArrayList<>();
        for (int jid = 0; jid < PADS.length; jid++) {
            Pad pad = PADS[jid];
            if (!pad.connected) continue;
            Map<String, Boolean> buttons = new LinkedHashMap<>();
            for (int button = 0; button < BUTTONS; button++) buttons.put(NAMES.get(button), pad.buttons[button]);
            buttons.put(NAMES.get(TRIGGER_L), pad.triggers[0] > TRIGGER_DOWN);
            buttons.put(NAMES.get(TRIGGER_L + 1), pad.triggers[1] > TRIGGER_DOWN);
            out.add(new DevicesRef.Gamepad(jid, pad.sticks[0], pad.sticks[1], pad.triggers[0], pad.triggers[1], buttons));
        }
        return out;
    }
}
