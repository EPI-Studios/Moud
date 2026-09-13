package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.InputAction;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.mod.MoudMod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

// the input actions a client holds, read off the keyboard and mouse once a frame
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
        // a screen owns the keyboard: typing an e in chat is not pressing interact
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

    // a letter or a digit names what is printed on the key, not where it sits: glfw's key codes are the
    // positions of a us keyboard, so "m" read as a code was the comma on an azerty one and "q" was its a.
    // the layout is asked what each printable key types, and asked again when a name is not found in case
    // the player switched layouts
    private static final Map<Character, Integer> TYPED = new HashMap<>();

    private static Integer typed(char c) {
        char wanted = Character.toLowerCase(c);
        Integer code = TYPED.get(wanted);
        if (code != null) return code;
        TYPED.clear();
        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_GRAVE_ACCENT; key++) {
            String name = GLFW.glfwGetKeyName(key, 0);
            if (name != null && name.length() == 1) TYPED.putIfAbsent(Character.toLowerCase(name.charAt(0)), key);
        }
        return TYPED.get(wanted);
    }

    // null when no action of that name exists, so input can fall back to the built in ones
    public static Boolean down(String action) {
        return DOWN.get(action);
    }

    // whether any of the keys named is down right now
    public static boolean pressed(String keys) {
        return pressed(Minecraft.getInstance().getWindow().handle(), keys);
    }

    private static boolean pressed(long window, String keys) {
        for (String raw : keys.split(",")) {
            String name = raw.trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) continue;
            Integer code = name.length() == 1 ? typed(name.charAt(0)) : null;
            if (code == null) code = KEYS.get(name);
            if (code == null) {
                if (UNKNOWN.add(name)) MoudMod.LOG.warn("an input action names the key '{}', which is not one of {}", raw.trim(), KEYS.keySet());
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
