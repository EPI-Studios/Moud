package com.moud.client.fabric.scripting.api;

import java.util.HashMap;
import java.util.Map;

public final class TimerApi {

    private final Map<String, Double> remaining = new HashMap<>();
    private final Map<String, Double> startDuration = new HashMap<>();

    public TimerApi() {}

    public void start(String name, double duration) {
        remaining.put(name, duration);
        startDuration.put(name, duration);
    }

    public void cancel(String name) {
        remaining.remove(name);
        startDuration.remove(name);
    }

    public boolean isActive(String name) {
        return remaining.containsKey(name);
    }

    public double elapsed(String name) {
        Double start = startDuration.get(name);
        Double rem = remaining.get(name);
        if (start == null || rem == null) return 0.0;
        return start - rem;
    }

    public void tick(double dt) {
        remaining.entrySet().removeIf(entry -> {
            double newVal = entry.getValue() - dt;
            if (newVal <= 0.0) {
                startDuration.remove(entry.getKey());
                return true;
            }
            entry.setValue(newVal);
            return false;
        });
    }
}
