package com.moud.client.fabric.input;

import com.moud.core.input.CoreInputActions;
import com.moud.core.input.InputAction;
import com.moud.core.input.InputBinding;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class ClientInputMap {

    private static final String CATEGORY = "category.moud";

    private static final Map<String, Function<GameOptions, KeyBinding>> MC_MIRRORED = new LinkedHashMap<>();
    static {
        MC_MIRRORED.put("forward",   o -> o.forwardKey);
        MC_MIRRORED.put("back",      o -> o.backKey);
        MC_MIRRORED.put("left",      o -> o.leftKey);
        MC_MIRRORED.put("right",     o -> o.rightKey);
        MC_MIRRORED.put("jump",      o -> o.jumpKey);
        MC_MIRRORED.put("sprint",    o -> o.sprintKey);
        MC_MIRRORED.put("sneak",     o -> o.sneakKey);
        MC_MIRRORED.put("attack",    o -> o.attackKey);
        MC_MIRRORED.put("use",       o -> o.useKey);
        MC_MIRRORED.put("pick_item", o -> o.pickItemKey);
    }

    private static final Map<String, KeyBinding> MOUD_KEYBINDS = new LinkedHashMap<>();
    private static final Map<String, Boolean> currentDown = new HashMap<>();
    private static final Map<String, Boolean> previousDown = new HashMap<>();
    private static boolean registered = false;

    private ClientInputMap() { }

    public static void ensureRegistered() {
        if (registered) return;
        registered = true;
        for (InputAction action : CoreInputActions.defaults().values()) {
            if (MC_MIRRORED.containsKey(action.id())) continue;
            if (MOUD_KEYBINDS.containsKey(action.id())) continue;
            int code = InputUtil.UNKNOWN_KEY.getCode();
            for (InputBinding b : action.defaultBindings()) {
                if (b.kind() == InputBinding.Kind.KEY) {
                    code = b.code();
                    break;
                }
            }
            KeyBinding kb = new KeyBinding(
                    "key.moud." + action.id(),
                    InputUtil.Type.KEYSYM,
                    code,
                    CATEGORY
            );
            KeyBindingHelper.registerKeyBinding(kb);
            MOUD_KEYBINDS.put(action.id(), kb);
        }
    }

    public static void poll() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        GameOptions opts = mc.options;

        for (Map.Entry<String, Function<GameOptions, KeyBinding>> e : MC_MIRRORED.entrySet()) {
            String id = e.getKey();
            previousDown.put(id, currentDown.getOrDefault(id, false));
            KeyBinding kb = e.getValue().apply(opts);
            currentDown.put(id, kb != null && kb.isPressed());
        }
        for (Map.Entry<String, KeyBinding> e : MOUD_KEYBINDS.entrySet()) {
            String id = e.getKey();
            previousDown.put(id, currentDown.getOrDefault(id, false));
            KeyBinding kb = e.getValue();
            currentDown.put(id, kb != null && kb.isPressed());
        }
    }

    public static boolean isDown(String action) {
        return currentDown.getOrDefault(action, false);
    }

    public static boolean isPressed(String action) {
        return currentDown.getOrDefault(action, false) && !previousDown.getOrDefault(action, false);
    }

    public static boolean isReleased(String action) {
        return !currentDown.getOrDefault(action, false) && previousDown.getOrDefault(action, false);
    }

    public static boolean hasAction(String action) {
        return action != null && (MC_MIRRORED.containsKey(action) || MOUD_KEYBINDS.containsKey(action));
    }

    public static Set<String> allActions() {
        Set<String> s = new HashSet<>();
        s.addAll(MC_MIRRORED.keySet());
        s.addAll(MOUD_KEYBINDS.keySet());
        return s;
    }

    public static List<String> actionIds() {
        return List.copyOf(allActions());
    }

    public static KeyBinding resolveBinding(String action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return null;
        Function<GameOptions, KeyBinding> m = MC_MIRRORED.get(action);
        if (m != null) return m.apply(mc.options);
        return MOUD_KEYBINDS.get(action);
    }

    public static String boundKeyToken(String action) {
        KeyBinding kb = resolveBinding(action);
        if (kb == null) return "";
        return kb.getBoundKeyTranslationKey();
    }

    public static boolean setMoudBinding(String action, String token) {
        if (!MOUD_KEYBINDS.containsKey(action)) return false;
        InputBinding parsed = InputBinding.parse(token);
        if (parsed == null) return false;
        InputUtil.Type type = switch (parsed.kind()) {
            case MOUSE_BUTTON -> InputUtil.Type.MOUSE;
            case KEY, GAMEPAD_BUTTON -> InputUtil.Type.KEYSYM;
        };
        KeyBinding kb = MOUD_KEYBINDS.get(action);
        kb.setBoundKey(type.createFromCode(parsed.code()));
        KeyBinding.updateKeysByCode();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            mc.options.write();
        }
        return true;
    }

    public static void resetMoudBinding(String action) {
        KeyBinding kb = MOUD_KEYBINDS.get(action);
        if (kb == null) return;
        InputAction def = CoreInputActions.defaults().get(action);
        int code = InputUtil.UNKNOWN_KEY.getCode();
        if (def != null) {
            for (InputBinding b : def.defaultBindings()) {
                if (b.kind() == InputBinding.Kind.KEY) {
                    code = b.code();
                    break;
                }
            }
        }
        kb.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(code));
        KeyBinding.updateKeysByCode();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            mc.options.write();
        }
    }

    public static boolean isMoudAction(String action) {
        return MOUD_KEYBINDS.containsKey(action);
    }
}
