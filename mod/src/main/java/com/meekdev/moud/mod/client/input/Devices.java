package com.meekdev.moud.mod.client.input;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.client.ClientPlace;
import com.meekdev.moud.mod.client.EditMode;
import com.meekdev.moud.mod.client.WindowApi;
import com.meekdev.moud.script.api.DevicesRef;
import com.meekdev.moud.script.host.Host;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Devices implements DevicesRef {

    private static final Set<Integer> SUNK = new HashSet<>();

    private final Input input;

    public Devices(Input input) {
        this.input = input;
    }

    public static void reset() {
        SUNK.clear();
        lookSunk = false;
        Gamepads.reset();
    }

    public static boolean key(int key, int action) {
        Host host = ClientPlace.host();
        if (host == null || key == GLFW.GLFW_KEY_UNKNOWN) return false;
        if (action == GLFW.GLFW_REPEAT) return SUNK.contains(key);
        return send(host, key, new Event("keyboard", Actions.name(key), state(action), position(0), Vector3.ZERO, processed()), action);
    }

    public static boolean button(int button, int action, boolean taken) {
        boolean detector = !taken && !processed() && action != GLFW.GLFW_REPEAT && ClickDetectors.button(button, action == GLFW.GLFW_PRESS);
        Host host = ClientPlace.host();
        if (host == null || button > GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return detector;
        int code = Actions.code("mousebutton" + (button + 1));
        Event event = new Event("mouseButton" + (button + 1), Actions.name(code), state(action), position(0), Vector3.ZERO, taken || detector || processed());
        return send(host, code, event, action) || detector;
    }

    public static boolean scroll(double amount) {
        Host host = ClientPlace.host();
        if (host == null || amount == 0) return false;
        return host.input(new Event("mouseWheel", "unknown", "change", position(Math.signum(amount)), new Vector3(0, 0, amount), processed()));
    }

    public static void frame(Input input) {
        Host host = ClientPlace.host();
        Gamepads.frame(host, processed());
        if (host == null || input.mouseDeltaX() == 0 && input.mouseDeltaY() == 0) {
            lookSunk = false;
            return;
        }
        lookSunk = host.input(new Event("mouseMovement", "unknown", "change", position(0),
                new Vector3(input.mouseDeltaX(), input.mouseDeltaY(), 0), processed()));
    }

    private static boolean lookSunk;

    public static boolean lookSunk() {
        return lookSunk;
    }

    private static boolean send(Host host, int code, Event event, int action) {
        boolean sunk = host.input(event);
        if (action != GLFW.GLFW_PRESS) return SUNK.remove(code);
        if (sunk) SUNK.add(code);
        else SUNK.remove(code);
        return sunk;
    }

    private static String state(int action) {
        return action == GLFW.GLFW_RELEASE ? "end" : "begin";
    }

    private static Vector3 position(double z) {
        Input input = ClientPlace.input();
        return new Vector3(input.mouseX(), input.mouseY(), z);
    }

    static boolean processed() {
        Minecraft client = Minecraft.getInstance();
        return client.screen != null || client.getOverlay() != null || EditMode.editing();
    }

    @Override
    public boolean keyDown(String key) {
        return Actions.pressed(Actions.code(key));
    }

    @Override
    public boolean mouseButtonDown(int button) {
        return Actions.pressed(Actions.code("mousebutton" + button));
    }

    @Override
    public List<String> keysDown() {
        return Actions.keysDown();
    }

    @Override
    public String keyCode(String key) {
        return Actions.name(Actions.code(key));
    }

    @Override
    public String keyName(String key) {
        KeyMapping mapping = input.mapping(key);
        if (mapping != null) return mapping.getTranslatedKeyMessage().getString();
        return Actions.label(Actions.code(key));
    }

    @Override
    public String mouseBehavior() {
        if (Input.pointerHeld()) return "lockCurrentPosition";
        return input.mouseLocked() ? "lockCenter" : "default";
    }

    @Override
    public void mouseBehavior(String behavior) {
        switch (behavior) {
            case "lockCenter" -> input.lockMouse(true);
            case "lockCurrentPosition" -> {
                input.lockMouse(false);
                Input.hold(true);
            }
            default -> input.lockMouse(false);
        }
    }

    @Override
    public boolean mouseIconEnabled() {
        return WindowApi.INSTANCE.cursorVisible();
    }

    @Override
    public void mouseIconEnabled(boolean on) {
        WindowApi.INSTANCE.cursorVisible(on);
    }

    @Override
    public List<Gamepad> gamepads() {
        return Gamepads.all();
    }
}
