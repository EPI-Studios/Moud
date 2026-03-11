package com.moud.client.fabric.runtime;

import org.lwjgl.glfw.GLFW;

final class PlayRuntimeInputState {
    private boolean forward;
    private boolean back;
    private boolean left;
    private boolean right;
    private boolean jump;
    private boolean sprint;

    void clear() {
        forward = back = left = right = jump = sprint = false;
    }

    void onKeyEvent(int key, int action) {
        boolean down = action != GLFW.GLFW_RELEASE;
        switch (key) {
            case GLFW.GLFW_KEY_W -> forward = down;
            case GLFW.GLFW_KEY_S -> back = down;
            case GLFW.GLFW_KEY_A -> left = down;
            case GLFW.GLFW_KEY_D -> right = down;
            case GLFW.GLFW_KEY_SPACE -> jump = down;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> sprint = down;
            default -> {
            }
        }
    }

    Movement movement() {
        float moveX = (right ? 1.0f : 0.0f) + (left ? -1.0f : 0.0f);
        float moveZ = (forward ? 1.0f : 0.0f) + (back ? -1.0f : 0.0f);
        float len = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (len > 1e-6f && len > 1.0f) {
            moveX /= len;
            moveZ /= len;
        }
        return new Movement(moveX, moveZ);
    }

    boolean jump() {
        return jump;
    }

    boolean sprint() {
        return sprint;
    }

    record Movement(float moveX, float moveZ) {
    }
}

