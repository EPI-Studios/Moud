package com.moud.core.input;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class CoreInputActions {

    public static final int KEY_W = 87;
    public static final int KEY_A = 65;
    public static final int KEY_S = 83;
    public static final int KEY_D = 68;
    public static final int KEY_SPACE = 32;
    public static final int KEY_LSHIFT = 340;
    public static final int KEY_LCTRL = 341;
    public static final int KEY_E = 69;
    public static final int KEY_R = 82;
    public static final int KEY_F = 70;
    public static final int KEY_ESCAPE = 256;
    public static final int MOUSE_LEFT = 0;
    public static final int MOUSE_RIGHT = 1;
    public static final int MOUSE_MIDDLE = 2;

    private CoreInputActions() { }

    public static Map<String, InputAction> defaults() {
        LinkedHashMap<String, InputAction> out = new LinkedHashMap<>();
        add(out, "forward", "Forward", InputBinding.key(KEY_W));
        add(out, "back", "Back", InputBinding.key(KEY_S));
        add(out, "left", "Strafe Left", InputBinding.key(KEY_A));
        add(out, "right", "Strafe Right", InputBinding.key(KEY_D));
        add(out, "jump", "Jump", InputBinding.key(KEY_SPACE));
        add(out, "sprint", "Sprint", InputBinding.key(KEY_LCTRL));
        add(out, "sneak", "Sneak", InputBinding.key(KEY_LSHIFT));
        add(out, "attack", "Attack", InputBinding.mouse(MOUSE_LEFT));
        add(out, "use", "Use", InputBinding.mouse(MOUSE_RIGHT));
        add(out, "reload", "Reload", InputBinding.key(KEY_R));
        add(out, "interact", "Interact", InputBinding.key(KEY_E));
        add(out, "menu", "Menu", InputBinding.key(KEY_ESCAPE));
        return out;
    }

    private static void add(Map<String, InputAction> map, String id, String display, InputBinding... bindings) {
        map.put(id, new InputAction(id, display, List.of(bindings)));
    }
}
