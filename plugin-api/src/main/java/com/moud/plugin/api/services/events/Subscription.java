package com.moud.plugin.api.services.events;

public interface Subscription extends AutoCloseable {
    void unsubscribe();
    boolean active();

    @Override
    default void close() {
        unsubscribe();
    }
}
