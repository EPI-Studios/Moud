package com.meekdev.moud.mod.client.input;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.input.InputAction;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.MoudMod;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import com.meekdev.moud.mod.client.ClientScene;

public final class Actions {

    private static final Map<String, Integer> KEYS = new HashMap<>();
    private static final int MOUSE = 1 << 16;

    static {
        for (char c = 'A'; c <= 'Z'; c++) KEYS.put(String.valueOf(c).toLowerCase(Locale.ROOT), GLFW.GLFW_KEY_A + (c - 'A'));
        for (char c = '0'; c <= '9'; c++) KEYS.put(String.valueOf(c), GLFW.GLFW_KEY_0 + (c - '0'));
        for (int n = 1; n <= 12; n++) KEYS.put("f" + n, GLFW.GLFW_KEY_F1 + n - 1);
        KEYS.put("space", GLFW.GLFW_KEY_SPACE);
        KEYS.put("enter", GLFW.GLFW_KEY_ENTER);
        KEYS.put("tab", GLFW.GLFW_KEY_TAB);
        KEYS.put("escape", GLFW.GLFW_KEY_ESCAPE);
        KEYS.put("backspace", GLFW.GLFW_KEY_BACKSPACE);
        KEYS.put("leftshift", GLFW.GLFW_KEY_LEFT_SHIFT);
        KEYS.put("rightshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        KEYS.put("leftcontrol", GLFW.GLFW_KEY_LEFT_CONTROL);
        KEYS.put("rightcontrol", GLFW.GLFW_KEY_RIGHT_CONTROL);
        KEYS.put("leftalt", GLFW.GLFW_KEY_LEFT_ALT);
        KEYS.put("rightalt", GLFW.GLFW_KEY_RIGHT_ALT);
        KEYS.put("up", GLFW.GLFW_KEY_UP);
        KEYS.put("down", GLFW.GLFW_KEY_DOWN);
        KEYS.put("left", GLFW.GLFW_KEY_LEFT);
        KEYS.put("right", GLFW.GLFW_KEY_RIGHT);
        KEYS.put("mousebutton1", MOUSE | GLFW.GLFW_MOUSE_BUTTON_LEFT);
        KEYS.put("mousebutton2", MOUSE | GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        KEYS.put("mousebutton3", MOUSE | GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
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
                String name = GLFW.glfwGetKeyName(key, 0);
                if (name != null && name.length() == 1) TYPED.putIfAbsent(Character.toLowerCase(name.charAt(0)), key);
            }
        }
        return TYPED.get(Character.toLowerCase(c));
    }

    public static Boolean down(String action) {
        return DOWN.get(action);
    }

    public static boolean pressed(String keys) {
        return pressed(Minecraft.getInstance().getWindow().handle(), keys);
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
            boolean down = (code & MOUSE) != 0
                    ? GLFW.glfwGetMouseButton(window, code & ~MOUSE) == GLFW.GLFW_PRESS
                    : GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
            if (down) return true;
        }
        return false;
    }
}
