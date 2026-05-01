package com.moud.physics.api;

public record JointHandle(long id) {
    public static final JointHandle NONE = new JointHandle(0L);

    public boolean isValid() {
        return id != 0L;
    }
}
