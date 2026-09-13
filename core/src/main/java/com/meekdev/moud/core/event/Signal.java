package com.meekdev.moud.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Signal<T> {

    private static final List<?> EMPTY = List.of();

    @SuppressWarnings("unchecked")
    private List<Consumer<T>> handlers = (List<Consumer<T>>) EMPTY;

    public Connection connect(Consumer<T> handler) {
        List<Consumer<T>> next = new ArrayList<>(handlers.size() + 1);
        next.addAll(handlers);
        next.add(handler);
        handlers = next;
        return () -> disconnect(handler);
    }

    public void fire(T value) {
        List<Consumer<T>> snapshot = handlers;
        for (int i = 0; i < snapshot.size(); i++) {
            snapshot.get(i).accept(value);
        }
    }

    public int count() {
        return handlers.size();
    }

    private void disconnect(Consumer<T> handler) {
        int at = handlers.indexOf(handler);
        if (at < 0) return;
        List<Consumer<T>> next = new ArrayList<>(handlers);
        next.remove(at);
        handlers = next;
    }

    @FunctionalInterface
    public interface Connection {
        void disconnect();
    }
}
