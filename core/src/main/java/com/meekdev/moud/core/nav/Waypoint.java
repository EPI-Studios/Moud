package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.math.Vector3;

public record Waypoint(Vector3 position, Action action, String label) {

    public enum Action { WALK, JUMP, CLIMB, CUSTOM }

    public static Waypoint walk(Vector3 position) {
        return new Waypoint(position, Action.WALK, null);
    }
}
