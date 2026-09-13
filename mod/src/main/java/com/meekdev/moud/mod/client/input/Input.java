package com.meekdev.moud.mod.client.input;

import com.meekdev.moud.script.api.InputRef;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import com.meekdev.moud.mod.client.debug.CollisionView;

public final class Input implements InputRef {

    private static final KeyMapping.Category MOUD =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("moud", "moud"));

    private static KeyMapping pointer;

    public static void register() {
        pointer = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.moud.pointer",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, MOUD));
        CollisionView.register(MOUD);
    }

    private static boolean pointerReleased;

    public static void pointerFrame() {
        Minecraft client = Minecraft.getInstance();
        if (pointer == null || client.player == null || client.screen != null) {
            pointerReleased = false;
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
        actions.put("forward", () -> options.keyUp);
        actions.put("back", () -> options.keyDown);
        actions.put("left", () -> options.keyLeft);
        actions.put("right", () -> options.keyRight);
        actions.put("jump", () -> options.keyJump);
        actions.put("sneak", () -> options.keyShift);
        actions.put("sprint", () -> options.keySprint);
        actions.put("attack", () -> options.keyAttack);
        actions.put("use", () -> options.keyUse);
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
        Minecraft client = Minecraft.getInstance();
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
