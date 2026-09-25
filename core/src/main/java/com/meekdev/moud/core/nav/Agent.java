package com.meekdev.moud.core.nav;

import java.util.Map;

public record Agent(double radius, double height, boolean canJump, boolean canClimb, double spacing, Map<String, Double> costs) {

    public static final Agent PLAYER = new Agent(0.3, 1.8, true, true, 0, Map.of());

    public record Shape(double radius, double height, boolean canJump) {}

    public Agent {
        radius = Double.isNaN(radius) ? 0.3 : Math.clamp(radius, 0.1, 4);
        height = Double.isNaN(height) ? 1.8 : Math.clamp(height, 0.5, 16);
        spacing = spacing > 0 ? Math.max(spacing, 0.25) : 0;
        costs = Map.copyOf(costs);
    }

    public Shape shape() {
        return new Shape(Math.round(radius * 20) / 20.0, Math.round(height * 10) / 10.0, canJump);
    }
}
