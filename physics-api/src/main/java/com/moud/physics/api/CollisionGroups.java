package com.moud.physics.api;

public record CollisionGroups(int layer, int mask) {
    public static final CollisionGroups DEFAULT = new CollisionGroups(0x0001, 0xFFFF);
}
