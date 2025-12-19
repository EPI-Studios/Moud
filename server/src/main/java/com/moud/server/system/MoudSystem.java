package com.moud.server.system;

@FunctionalInterface
public interface MoudSystem {
    void onTick(float deltaTime);

    default void onShutdown() {
    }
}

