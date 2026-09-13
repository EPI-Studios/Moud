package com.meekdev.moud.script.host;

public record Suspend(Waiter waiter, Runnable cancel) {

    @FunctionalInterface
    public interface Waiter {
        Object[] poll(double dt);
    }

    public Suspend(Waiter waiter) {
        this(waiter, () -> {});
    }

    public static Suspend seconds(double seconds) {
        double[] waited = {0};
        double wanted = Math.max(0, seconds);
        return new Suspend(dt -> {
            waited[0] += dt;
            return waited[0] >= wanted ? new Object[] {waited[0]} : null;
        });
    }
}
