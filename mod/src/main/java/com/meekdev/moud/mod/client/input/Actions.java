package com.meekdev.moud.mod.client.input;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.input.InputAction;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.JsonResources;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Actions {

    private static final String KEY_TABLE = "/assets/moud/input/keys.json";
    private static final Map<String, Integer> KEYS = new HashMap<>();
    private static final Map<Integer, String> NAMES = new HashMap<>();
    private static final int MOUSE = 1 << 16;
    private static final int GAMEPAD = 1 << 17;
    public static final int WHEEL = MOUSE | 16;
    public static final int MOVEMENT = MOUSE | 17;

    static {
        for (char c = 'a'; c <= 'z'; c++) key(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'a'));
        for (char c = '0'; c <= '9'; c++) key(String.valueOf(c), GLFW.GLFW_KEY_0 + (c - '0'));
        for (int n = 1; n <= 12; n++) key("f" + n, GLFW.GLFW_KEY_F1 + n - 1);
        for (int n = 0; n <= 9; n++) key("keypad" + n, GLFW.GLFW_KEY_KP_0 + n);
        JsonObject table = JsonResources.read(KEY_TABLE);
        for (Map.Entry<String, JsonElement> key : table.getAsJsonObject("keys").entrySet()) {
            key(key.getKey(), key.getValue().getAsInt());
        }
        for (Map.Entry<String, JsonElement> button : table.getAsJsonObject("mouseButtons").entrySet()) {
            key(button.getKey(), MOUSE | button.getValue().getAsInt());
        }
        key("mousewheel", WHEEL);
        key("mousemovement", MOVEMENT);
        for (int n = 0; n < Gamepads.NAMES.size(); n++) key(Gamepads.NAMES.get(n), GAMEPAD | n);
    }

    private static void key(String name, int code) {
        KEYS.put(name.toLowerCase(Locale.ROOT), code);
        NAMES.putIfAbsent(code, name);
    }

    private static final Map<String, Boolean> DOWN = new HashMap<>();
    private static final Map<InputAction, Boolean> WAS = new IdentityHashMap<>();
    private static final Set<String> UNKNOWN = new HashSet<>();

    private Actions() {}

    public static void frame() {
        DOWN.clear();
        InstanceTree tree = ClientScene.tree();
        if (tree == null) {
            WAS.clear();
            return;
        }
        Minecraft client = Minecraft.getInstance();
        boolean listening = client.screen == null;
        long window = client.getWindow().handle();
        WAS.keySet().removeIf(action -> !action.isAlive());
        for (InputAction action : tree.ofClass(Classes.INPUT_ACTION)) {
            boolean down = listening && action.enabled && pressed(window, action.keys);
            DOWN.merge(action.name(), down, Boolean::logicalOr);
            boolean was = WAS.getOrDefault(action, false);
            WAS.put(action, down);
            if (down && !was) action.began.fire(action);
            if (!down && was) action.ended.fire(action);
        }
    }

    private static final int[] PRINTABLE = printable();
    private static final Map<Character, Integer> TYPED = new HashMap<>();
    private static long typedAt;

    private static int[] printable() {
        int[] keys = new int[64];
        int n = 0;
        keys[n++] = GLFW.GLFW_KEY_APOSTROPHE;
        for (int key = GLFW.GLFW_KEY_COMMA; key <= GLFW.GLFW_KEY_9; key++) keys[n++] = key;
        keys[n++] = GLFW.GLFW_KEY_SEMICOLON;
        keys[n++] = GLFW.GLFW_KEY_EQUAL;
        for (int key = GLFW.GLFW_KEY_A; key <= GLFW.GLFW_KEY_RIGHT_BRACKET; key++) keys[n++] = key;
        keys[n++] = GLFW.GLFW_KEY_GRAVE_ACCENT;
        keys[n++] = GLFW.GLFW_KEY_WORLD_1;
        keys[n++] = GLFW.GLFW_KEY_WORLD_2;
        return Arrays.copyOf(keys, n);
    }

    private static Integer keyForChar(char c) {
        long now = System.nanoTime();
        if (TYPED.isEmpty() || now - typedAt > 2_000_000_000L) {
            typedAt = now;
            TYPED.clear();
            for (int key : PRINTABLE) {
                if (GLFW.glfwGetKeyScancode(key) < 0) continue;
                String name = GLFW.glfwGetKeyName(key, 0);
                if (name != null && name.length() == 1) TYPED.putIfAbsent(Character.toLowerCase(name.charAt(0)), key);
            }
        }
        return TYPED.get(Character.toLowerCase(c));
    }

    public static Boolean down(String action) {
        return DOWN.get(action);
    }

    public static int code(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        Integer code = key.length() == 1 ? keyForChar(key.charAt(0)) : null;
        if (code == null) code = KEYS.get(key);
        if (code == null) throw new IllegalArgumentException("'" + name + "' is not a key, expected a letter, a digit or one of " + String.join(", ", KEYS.keySet()));
        return code;
    }

    public static String name(int code) {
        boolean digit = code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9;
        if (code < GLFW.GLFW_KEY_ESCAPE && !digit && GLFW.glfwGetKeyScancode(code) >= 0) {
            String label = GLFW.glfwGetKeyName(code, 0);
            if (label != null && label.length() == 1) return label.toLowerCase(Locale.ROOT);
        }
        return NAMES.getOrDefault(code, "unknown");
    }

    public static String label(int code) {
        if ((code & GAMEPAD) != 0) return Gamepads.NAMES.get(code & ~GAMEPAD);
        if (code == WHEEL) return "Mouse Wheel";
        if (code == MOVEMENT) return "Mouse Movement";
        InputConstants.Key key = (code & MOUSE) != 0
                ? InputConstants.Type.MOUSE.getOrCreate(code & ~MOUSE)
                : InputConstants.Type.KEYSYM.getOrCreate(code);
        return key.getDisplayName().getString();
    }

    public static List<String> keysDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        List<String> down = new ArrayList<>();
        for (int code = GLFW.GLFW_KEY_SPACE; code <= GLFW.GLFW_KEY_LAST; code++) {
            if (GLFW.glfwGetKey(window, code) != GLFW.GLFW_PRESS) continue;
            String name = name(code);
            if (!name.equals("unknown")) down.add(name);
        }
        return down;
    }

    public static boolean bindable(int code) {
        return code <= (MOUSE | GLFW.GLFW_MOUSE_BUTTON_LAST);
    }

    public static boolean mouse(int code) {
        return (code & MOUSE) != 0;
    }

    public static int button(int code) {
        return code & ~MOUSE;
    }

    public static boolean pressed(String keys) {
        return pressed(Minecraft.getInstance().getWindow().handle(), keys);
    }

    public static boolean pressed(int code) {
        return pressed(Minecraft.getInstance().getWindow().handle(), code);
    }

    private static boolean pressed(long window, int code) {
        if ((code & GAMEPAD) != 0) return Gamepads.down(code & ~GAMEPAD);
        if ((code & MOUSE) == 0) return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
        int button = code & ~MOUSE;
        return button <= GLFW.GLFW_MOUSE_BUTTON_LAST && GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }

    private static boolean pressed(long window, String keys) {
        for (String raw : keys.split(",")) {
            String name = raw.trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) continue;
            Integer code = name.length() == 1 ? keyForChar(name.charAt(0)) : null;
            if (code == null) code = KEYS.get(name);
            if (code == null) {
                if (UNKNOWN.add(name)) MoudMod.LOG.warn("unknown key '{}' in input action, expected one of {}", raw.trim(), KEYS.keySet());
                continue;
            }
            if (pressed(window, code)) return true;
        }
        return false;
    }
}
