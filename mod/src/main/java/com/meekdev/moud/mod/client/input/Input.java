package com.meekdev.moud.mod.client.input;

import com.meekdev.moud.mod.client.debug.CollisionView;
import com.meekdev.moud.script.api.InputRef;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class Input implements InputRef {

    private static final KeyMapping.Category MOUD =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("moud", "moud"));

    public static final Map<String, Function<Options, KeyMapping>> GAME_ACTIONS = gameActions();

    private static KeyMapping pointer;

    private static Map<String, Function<Options, KeyMapping>> gameActions() {
        Map<String, Function<Options, KeyMapping>> actions = new LinkedHashMap<>();
        actions.put("forward", options -> options.keyUp);
        actions.put("back", options -> options.keyDown);
        actions.put("left", options -> options.keyLeft);
        actions.put("right", options -> options.keyRight);
        actions.put("jump", options -> options.keyJump);
        actions.put("sneak", options -> options.keyShift);
        actions.put("sprint", options -> options.keySprint);
        actions.put("attack", options -> options.keyAttack);
        actions.put("use", options -> options.keyUse);
        return Collections.unmodifiableMap(actions);
    }

    public static void register() {
        pointer = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.moud.pointer",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, MOUD));
        CollisionView.register(MOUD);
    }

    private static boolean pointerReleased;
    private static boolean scriptFree;

    public static boolean scriptWantsPointer() {
        return scriptFree;
    }

    public static void resetPointer() {
        scriptFree = false;
    }

    public static void pointerFrame() {
        Minecraft client = Minecraft.getInstance();
        if (pointer == null || client.player == null || client.screen != null) {
            pointerReleased = false;
            return;
        }
        if (scriptFree) {
            if (client.mouseHandler.isMouseGrabbed()) client.mouseHandler.releaseMouse();
            return;
        }
        boolean held = pointer.isDown();
        if (held && client.mouseHandler.isMouseGrabbed()) {
            client.mouseHandler.releaseMouse();
            pointerReleased = true;
        } else if (!held && pointerReleased) {
            pointerReleased = false;
            if (!client.mouseHandler.isMouseGrabbed()) client.mouseHandler.grabMouse();
        }
    }

    private final Map<String, Supplier<KeyMapping>> actions = new LinkedHashMap<>();

    private double lastX;
    private double lastY;
    private double x;
    private double y;
    private double dx;
    private double dy;

    public Input() {
        Options options = Minecraft.getInstance().options;
        GAME_ACTIONS.forEach((name, key) -> actions.put(name, () -> key.apply(options)));
        actions.put("pointer", () -> pointer);
    }

    public void poll() {
        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        double across = window.getScreenWidth() == 0 ? 1 : window.getScreenWidth();
        double down = window.getScreenHeight() == 0 ? 1 : window.getScreenHeight();
        double raw = client.mouseHandler.xpos();
        double rawDown = client.mouseHandler.ypos();

        dx = raw - lastX;
        dy = rawDown - lastY;
        lastX = raw;
        lastY = rawDown;
        x = raw * window.getGuiScaledWidth() / across;
        y = rawDown * window.getGuiScaledHeight() / down;
    }

    @Override
    public boolean down(String action) {
        Boolean placed = Actions.down(action);
        if (placed != null) return placed;
        Supplier<KeyMapping> key = actions.get(action);
        return key != null && key.get() != null && key.get().isDown();
    }

    @Override
    public boolean known(String action) {
        return actions.containsKey(action) || Actions.down(action) != null;
    }

    @Override
    public boolean mouseLocked() {
        return Minecraft.getInstance().mouseHandler.isMouseGrabbed();
    }

    @Override
    public void lockMouse(boolean locked) {
        scriptFree = !locked;
        Minecraft client = Minecraft.getInstance();
        if (locked && client.screen != null) return;
        if (locked == client.mouseHandler.isMouseGrabbed()) return;
        if (locked) client.mouseHandler.grabMouse(); else client.mouseHandler.releaseMouse();
    }

    @Override
    public double sensitivity() {
        return Minecraft.getInstance().options.sensitivity().get();
    }

    @Override
    public void sensitivity(double value) {
        Minecraft.getInstance().options.sensitivity().set(Math.max(0, Math.min(1, value)));
    }

    @Override
    public double mouseX() {
        return x;
    }

    @Override
    public double mouseY() {
        return y;
    }

    @Override
    public double mouseDeltaX() {
        return dx;
    }

    @Override
    public double mouseDeltaY() {
        return dy;
    }

    @Override
    public double screenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Override
    public double screenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
