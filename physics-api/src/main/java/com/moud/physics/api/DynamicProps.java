package com.moud.physics.api;

public record DynamicProps(
        float mass,
        float gravityScale,
        float linearDamping,
        float angularDamping,
        boolean ccd
) {
    public static DynamicProps defaults() {
        return new DynamicProps(1f, 1f, 0f, 0f, false);
    }
}
