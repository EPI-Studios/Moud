package com.meekdev.moud.core.tween;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.event.Signal;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.interp.Blend;
import java.util.List;

// properties of one instance moved from where they were toward goals over time
public final class Tween {

    public record Goal(PropertyDef property, Object to) {}

    public record Info(double time, Easing easing, Easing.Direction direction, int repeats, boolean reverses, double delay) {
        public static final Info DEFAULT = new Info(1, Easing.QUAD, Easing.Direction.OUT, 0, false, 0);
    }

    public enum State { PLAYING, PAUSED, COMPLETED, CANCELLED }

    // fires once with true when it ran out, or false when it was cancelled
    public final Signal<Boolean> completed = new Signal<>();

    private final Instance instance;
    private final List<Goal> goals;
    private final Info info;
    private Object[] from;
    private double clock;
    private int round;
    private State state = State.PLAYING;

    public Tween(Instance instance, List<Goal> goals, Info info) {
        this.instance = instance;
        this.goals = List.copyOf(goals);
        this.info = info;
    }

    public State state() {
        return state;
    }

    public Instance instance() {
        return instance;
    }

    public void pause() {
        if (state == State.PLAYING) state = State.PAUSED;
    }

    public void resume() {
        if (state == State.PAUSED) state = State.PLAYING;
    }

    public void cancel() {
        if (state == State.COMPLETED || state == State.CANCELLED) return;
        state = State.CANCELLED;
        completed.fire(false);
    }

    // true while it has more to do
    public boolean step(double dt) {
        if (state == State.COMPLETED || state == State.CANCELLED) return false;
        if (!instance.isAlive()) {
            cancel();
            return false;
        }
        if (state == State.PAUSED) return true;
        clock += dt;
        double active = clock - info.delay();
        if (active < 0) return true;
        // the start is read when it actually starts, so a delayed tween begins from where things are then
        if (from == null) from = current();

        double time = Math.max(1e-9, info.time());
        double t = Math.min(1, active / time);
        boolean back = info.reverses() && round % 2 == 1;
        double eased = info.easing().apply(back ? 1 - t : t, info.direction());
        apply(eased);
        if (t < 1) return true;

        int rounds = (info.repeats() < 0 ? Integer.MAX_VALUE : info.repeats() + 1) * (info.reverses() ? 2 : 1);
        round++;
        if (round < rounds) {
            clock = info.delay() + (active - time);
            return true;
        }
        state = State.COMPLETED;
        completed.fire(true);
        return false;
    }

    private Object[] current() {
        Object[] values = new Object[goals.size()];
        for (int i = 0; i < goals.size(); i++) {
            PropertyDef property = goals.get(i).property();
            values[i] = property.isNumeric() ? (Object) property.getNum(instance)
                    : property.type().isBool() ? (Object) property.getBool(instance) : property.getObj(instance);
        }
        return values;
    }

    private void apply(double alpha) {
        for (int i = 0; i < goals.size(); i++) {
            PropertyDef property = goals.get(i).property();
            Object to = goals.get(i).to();
            if (property.isNumeric()) {
                Instances.setNum(instance, property, Blend.number(((Number) from[i]).doubleValue(), ((Number) to).doubleValue(), alpha));
            } else if (property.type().isBool()) {
                Instances.setBool(instance, property, alpha >= 1 ? (Boolean) to : (Boolean) from[i]);
            } else {
                Instances.setObj(instance, property, Blend.of(property.type(), from[i], to, alpha));
            }
        }
    }
}
