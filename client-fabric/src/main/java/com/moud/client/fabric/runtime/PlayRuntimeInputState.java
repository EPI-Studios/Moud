package com.moud.client.fabric.runtime;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

final class PlayRuntimeInputState {
    private boolean forwardDown;
    private boolean backDown;
    private boolean leftDown;
    private boolean rightDown;
    private boolean jumpDown;
    private boolean sprintDown;
    private boolean sneakDown;

    private float cachedMoveX;
    private float cachedMoveZ;
    private boolean cachedJump;
    private boolean cachedSprint;
    private boolean cachedSneak;
    private boolean hasCached;

    void captureKeys() {
        GameOptions opts = options();
        if (opts == null) {
            hasCached = false;
            return;
        }
        float moveX = 0f;
        if (rightDown) moveX += 1.0f;
        if (leftDown)  moveX -= 1.0f;
        float moveZ = 0f;
        if (forwardDown) moveZ += 1.0f;
        if (backDown)    moveZ -= 1.0f;
        float len = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (len > 1.0f) { moveX /= len; moveZ /= len; }
        cachedMoveX  = moveX;
        cachedMoveZ  = moveZ;
        cachedJump   = jumpDown;
        cachedSprint = sprintDown;
        cachedSneak  = sneakDown;
        hasCached    = true;
    }

    void clear() {
        hasCached    = false;
        cachedMoveX  = 0f;
        cachedMoveZ  = 0f;
        cachedJump   = false;
        cachedSprint = false;
        cachedSneak  = false;
        forwardDown  = false;
        backDown     = false;
        leftDown     = false;
        rightDown    = false;
        jumpDown     = false;
        sprintDown   = false;
        sneakDown    = false;
    }

    void onKeyEvent(int key, int scancode, int action) {
        GameOptions opts = options();
        if (opts == null) {
            return;
        }
        boolean pressed = action != GLFW.GLFW_RELEASE;
        if (matches(opts.forwardKey, key, scancode)) forwardDown = pressed;
        if (matches(opts.backKey, key, scancode))    backDown = pressed;
        if (matches(opts.leftKey, key, scancode))    leftDown = pressed;
        if (matches(opts.rightKey, key, scancode))   rightDown = pressed;
        if (matches(opts.jumpKey, key, scancode))    jumpDown = pressed;
        if (matches(opts.sprintKey, key, scancode))  sprintDown = pressed;
        if (matches(opts.sneakKey, key, scancode))   sneakDown = pressed;
    }

    Movement movement() {
        if (hasCached) return new Movement(cachedMoveX, cachedMoveZ);
        float moveX = 0f;
        if (rightDown) moveX += 1.0f;
        if (leftDown)  moveX -= 1.0f;
        float moveZ = 0f;
        if (forwardDown) moveZ += 1.0f;
        if (backDown)    moveZ -= 1.0f;
        float len = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (len > 1.0f) { moveX /= len; moveZ /= len; }
        return new Movement(moveX, moveZ);
    }

    boolean jump() {
        return hasCached ? cachedJump : jumpDown;
    }

    boolean sprint() {
        return hasCached ? cachedSprint : sprintDown;
    }

    boolean sneak() {
        return hasCached ? cachedSneak : sneakDown;
    }

    private static GameOptions options() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null ? mc.options : null;
    }

    private static boolean matches(KeyBinding binding, int keyCode, int scanCode) {
        return binding != null && binding.matchesKey(keyCode, scanCode);
    }

    record Movement(float moveX, float moveZ) {}
}
