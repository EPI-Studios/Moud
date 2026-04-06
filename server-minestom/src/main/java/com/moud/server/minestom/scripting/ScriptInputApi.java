package com.moud.server.minestom.scripting;

import org.graalvm.polyglot.HostAccess;

public final class ScriptInputApi {
    private static final PlayerInputState EMPTY = new PlayerInputState("", 0L, 0f, 0f, 0f, 0f, 0f, 0f, false, false);
    private final InputMap inputMap;
    private PlayerInputState current;
    private PlayerInputState previous;

    ScriptInputApi(InputMap inputMap) {
        this.inputMap = inputMap;
    }

    void update(PlayerInputState input) {
        if (input == null) return;
        previous = current;
        current = input;
    }

    void clear() {
        previous = null;
        current = null;
    }

    private PlayerInputState current() {
        return current != null ? current : EMPTY;
    }

    private PlayerInputState previous() {
        return previous != null ? previous : EMPTY;
    }

    @HostAccess.Export
    public boolean is_action_pressed(String action) {
        return inputMap.isActionPressed(action, current());
    }

    @HostAccess.Export
    public boolean is_action_just_pressed(String action) {
        return inputMap.isActionJustPressed(action, current(), previous());
    }

    @HostAccess.Export
    public boolean is_action_just_released(String action) {
        return inputMap.isActionJustReleased(action, current(), previous());
    }

    @HostAccess.Export
    public float get_action_strength(String action) {
        return inputMap.getActionStrength(action, current());
    }

    @HostAccess.Export
    public float get_yaw() {
        return current().yawDeg();
    }

    @HostAccess.Export
    public float get_pitch() {
        return current().pitchDeg();
    }

    @HostAccess.Export
    public float get_cursor_x() {
        return current().cursorX();
    }

    @HostAccess.Export
    public float get_cursor_y() {
        return current().cursorY();
    }

    @HostAccess.Export
    public Vec2 get_cursor_position() {
        return new Vec2(current().cursorX(), current().cursorY());
    }

    @HostAccess.Export
    public float get_axis(String negative, String positive) {
        return inputMap.getAxis(negative, positive, current());
    }

    @HostAccess.Export
    public Vec2 get_vector(String negX, String posX, String negY, String posY) {
        float[] v = inputMap.getVector(negX, posX, negY, posY, current());
        return new Vec2(v[0], v[1]);
    }

    public static final class Vec2 {
        @HostAccess.Export
        public final float x;
        @HostAccess.Export
        public final float y;

        Vec2(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
