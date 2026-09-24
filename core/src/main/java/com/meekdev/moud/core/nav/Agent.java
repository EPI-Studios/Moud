package com.meekdev.moud.core.nav;

import java.util.Map;

public record Agent(double radius, double height, boolean canJump, boolean canClimb, double spacing, Map<String, Double> costs) {

    public static final Agent PLAYER = new Agent(0.3, 1.8, true, true, 0, Map.of());

    public record Shape(double radius, double height, boolean canJump) {}

    public Agent {
        radius = Math.clamp(radius, 0.1, 4);
        height = Math.clamp(height, 0.5, 16);
        spacing = Math.max(0, spacing);
        costs = Map.copyOf(costs);
    }

    public Shape shape() {
        return new Shape(radius, height, canJump);
    }
}
