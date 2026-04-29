package com.moud.physics.api;

public record BodyHandle(long id) {
    public static final BodyHandle NONE = new BodyHandle(0L);

    public boolean isValid() {
        return id != 0L;
    }
}
