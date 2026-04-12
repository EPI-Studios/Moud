package com.moud.server.minestom.scripting.input;


import com.moud.server.minestom.scripting.player.PlayerInputState;

import java.util.HashMap;
import java.util.Map;

public final class InputMap {
    @FunctionalInterface
    interface InputCondition {
        float strength(PlayerInputState input);
    }

    private final Map<String, InputCondition> actions = new HashMap<>();

    public InputMap() {
        actions.put("move_forward", i -> i.moveZ() > 0.5f ? i.moveZ() : 0f);
        actions.put("move_back", i -> i.moveZ() < -0.5f ? -i.moveZ() : 0f);
        actions.put("move_left", i -> i.moveX() < -0.5f ? -i.moveX() : 0f);
        actions.put("move_right", i -> i.moveX() > 0.5f ? i.moveX() : 0f);
        actions.put("jump", i -> i.jump() ? 1f : 0f);
        actions.put("sprint", i -> i.sprint() ? 1f : 0f);
    }

    public void addAction(String name, InputCondition condition) {
        if (name == null || name.isBlank() || condition == null) return;
        actions.put(name.trim(), condition);
    }

    public void removeAction(String name) {
        if (name != null) actions.remove(name.trim());
    }

    public boolean isActionPressed(String action, PlayerInputState input) {
        return getActionStrength(action, input) > 0f;
    }

    public boolean isActionJustPressed(String action, PlayerInputState current, PlayerInputState prev) {
        return getActionStrength(action, current) > 0f && getActionStrength(action, prev) == 0f;
    }

    public boolean isActionJustReleased(String action, PlayerInputState current, PlayerInputState prev) {
        return getActionStrength(action, current) == 0f && getActionStrength(action, prev) > 0f;
    }

    public float getActionStrength(String action, PlayerInputState input) {
        if (action == null || input == null) return 0f;
        InputCondition cond = actions.get(action.trim());
        if (cond == null) return 0f;
        float v = cond.strength(input);
        return Math.max(0f, Math.min(1f, v));
    }

    public float getAxis(String negative, String positive, PlayerInputState input) {
        float pos = getActionStrength(positive, input);
        float neg = getActionStrength(negative, input);
        return Math.max(-1f, Math.min(1f, pos - neg));
    }

    public float[] getVector(String negX, String posX, String negY, String posY, PlayerInputState input) {
        float x = getAxis(negX, posX, input);
        float y = getAxis(negY, posY, input);
        float len = (float) Math.sqrt(x * x + y * y);
        if (len > 1f) {
            x /= len;
            y /= len;
        }
        return new float[]{x, y};
    }
}
