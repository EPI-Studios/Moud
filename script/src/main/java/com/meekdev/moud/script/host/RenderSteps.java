package com.meekdev.moud.script.host;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class RenderSteps {

    static final double CAMERA = 200;

    private static final class Bound {
        final String name;
        final double priority;
        final Callable fn;
        boolean gone;

        Bound(String name, double priority, Callable fn) {
            this.name = name;
            this.priority = priority;
            this.fn = fn;
        }
    }

    private final Host host;
    private List<Bound> bound = List.of();
    private double dt;

    RenderSteps(Host host) {
        this.host = host;
    }

    void bind(String name, double priority, Callable fn) {
        unbind(name);
        Bound one = new Bound(name, priority, fn.retain());
        List<Bound> next = new ArrayList<>(bound);
        next.add(one);
        next.sort(Comparator.comparingDouble(b -> b.priority));
        bound = next;
        host.ownership().onRelease(host.ownership().current(), () -> {
            if (!one.gone) unbind(name);
        });
    }

    void unbind(String name) {
        List<Bound> next = new ArrayList<>(bound);
        for (Bound one : bound) {
            if (!one.name.equals(name)) continue;
            next.remove(one);
            one.gone = true;
            one.fn.release();
        }
        bound = next;
    }

    void beforeCamera(double dt) {
        this.dt = dt;
        run(true);
    }

    void afterCamera() {
        run(false);
    }

    private void run(boolean before) {
        for (Bound one : bound) {
            if (one.gone || one.priority <= CAMERA != before) continue;
            host.call(one.fn, "renderStep " + one.name, dt);
        }
    }

    void clear() {
        for (Bound one : bound) {
            one.gone = true;
            one.fn.release();
        }
        bound = List.of();
    }
}
