package com.moud.plugin.api.services.commands;

public interface RegisteredCommand extends AutoCloseable {
    String name();
    void unregister();

    @Override
    default void close() {
        unregister();
    }
}
