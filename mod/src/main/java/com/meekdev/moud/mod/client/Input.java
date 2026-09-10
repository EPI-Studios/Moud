package com.meekdev.moud.mod.client;

import com.meekdev.moud.script.api.InputRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

// what a place asks about the player, over minecraft's own bindings so a rebind in the vanilla
// controls screen is a rebind here too
//
// 7.4 polls this once at the top of the frame and hands the frame an immutable answer. reading the
// keyboard twice in one frame and getting two answers is how input bugs start
public final class Input implements InputRef {

    private final Map<String, Supplier<KeyMapping>> actions = new LinkedHashMap<>();

    private double lastX;
    private double lastY;
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
    }

    public void poll() {
        double x = Minecraft.getInstance().mouseHandler.xpos();
        double y = Minecraft.getInstance().mouseHandler.ypos();
        dx = x - lastX;
        dy = y - lastY;
        lastX = x;
        lastY = y;
    }

    @Override
    public boolean down(String action) {
        Supplier<KeyMapping> key = actions.get(action);
        return key != null && key.get().isDown();
    }

    @Override
    public boolean known(String action) {
        return actions.containsKey(action);
    }

    @Override
    public double mouseX() {
        return dx;
    }

    @Override
    public double mouseY() {
        return dy;
    }
}
