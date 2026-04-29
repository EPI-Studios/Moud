package com.moud.physics.api;

public record QueryFilter(int mask, long excludeBody) {
    public static final QueryFilter ALL = new QueryFilter(0xFFFF, 0L);
}
