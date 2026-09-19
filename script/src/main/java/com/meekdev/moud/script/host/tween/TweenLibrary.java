package com.meekdev.moud.script.host.tween;

import com.meekdev.moud.core.clazz.Enums;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.tween.Easing;
import com.meekdev.moud.core.tween.Tween;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.InstanceAccess;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class TweenLibrary {

    private record Handle(Tween tween, HostSignal completed) implements HostObject {

        private static final Members METHODS = new Members("Tween")
                .declare("completed", "AnySignal")
                .method("pause", "() -> ()", a -> {
                    a.self(Handle.class).tween().pause();
                    return null;
                })
                .method("resume", "() -> ()", a -> {
                    a.self(Handle.class).tween().resume();
                    return null;
                })
                .method("cancel", "() -> ()", a -> {
                    a.self(Handle.class).tween().cancel();
                    return null;
                })
                .method("state", "() -> string", a -> Enums.name(a.self(Handle.class).tween().state()));

        @Override
        public String typeName() {
            return "Tween";
        }

        @Override
        public Object get(String key) {
            return key.equals("completed") ? completed : METHODS.get(key);
        }
    }

    private TweenLibrary() {}

    public static void install(Host host) {
        host.api().declare(Handle.METHODS.decl());
        host.api().alias("TweenInfo", "{ time: number?, easing: string?, direction: string?, repeats: number?, reverses: boolean?, delay: number? }");
        List<Tween> running = new ArrayList<>();
        Consumer<Double> step = dt -> {
            for (Tween tween : new ArrayList<>(running)) {
                try {
                    if (!tween.step(dt)) running.remove(tween);
                } catch (RuntimeException e) {
                    running.remove(tween);
                    host.error("tween", e);
                }
            }
        };
        if (host.client()) host.onRenderStep(step); else host.onStep(step);

        host.instances().shared().method("tween", "(goals: { [string]: any }, info: TweenInfo?) -> Tween", a -> {
            Instance instance = a.self();
            List<Tween.Goal> goals = new ArrayList<>();
            for (Map.Entry<String, Object> entry : a.map(1).entrySet()) {
                goals.add(goal(host, instance, entry.getKey(), entry.getValue()));
            }
            Tween tween = new Tween(instance, goals, info(a.map(2, Map.of())));
            HostSignal completed = new HostSignal(host, "AnySignal", "tween.completed");
            tween.completed.connect(completed::fire);
            running.add(tween);
            host.ownership().onRelease(host.ownership().current(), tween::cancel);
            return new Handle(tween, completed);
        });
        if (host.lens() != null) CameraPaths.install(host);
    }

    private static Tween.Goal goal(Host host, Instance instance, String key, Object value) {
        if (instance instanceof Spatial spatial && (key.equals("position") || key.equals("rotation"))) {
            PropertyDef frame = instance.def().property("cframe");
            host.instances().checkWrite(instance, frame);
            CFrame world = Transforms.world(instance);
            CFrame target;
            if (key.equals("position")) {
                if (!(value instanceof Vector3 at)) throw new HostError("position expects a vec3");
                target = Transforms.localFor(instance, world.withPosition(at)).mul(CFrame.at(spatial.pivot));
            } else {
                if (!(value instanceof CFrame turn)) throw new HostError("rotation expects a cframe");
                target = spatial.cframe.withRotation(Transforms.localFor(instance, new CFrame(Vector3.ZERO, turn.rotation())).rotation());
            }
            return new Tween.Goal(frame, target);
        }
        PropertyDef property = instance.def().property(key);
        if (property == null) throw new HostError("%s has no property '%s'", instance.def().name(), key);
        if (property.readOnly() || property.engineWritten()) throw new HostError("%s.%s is read-only", instance.def().name(), property.name());
        host.instances().checkWrite(instance, property);
        return new Tween.Goal(property, InstanceAccess.parse(property, value));
    }

    private static Tween.Info info(Map<String, Object> table) {
        Tween.Info d = Tween.Info.DEFAULT;
        return new Tween.Info(number(table, "time", d.time()), option(table, "easing", Easing.class, d.easing()),
                option(table, "direction", Easing.Direction.class, d.direction()), (int) number(table, "repeats", d.repeats()),
                Boolean.TRUE.equals(table.get("reverses")), number(table, "delay", d.delay()));
    }

    static double number(Map<String, Object> table, String key, double fallback) {
        Object value = table.get(key);
        if (value == null) return fallback;
        if (value instanceof Number n) return n.doubleValue();
        throw new HostError("%s expects a number", key);
    }

    @SuppressWarnings("unchecked")
    static <E extends Enum<E>> E option(Map<String, Object> table, String key, Class<E> type, E fallback) {
        Object value = table.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String name)) throw new HostError("%s expects a string", key);
        try {
            return (E) Enums.parse(type, name);
        } catch (IllegalArgumentException e) {
            throw new HostError("%s: %s", key, e.getMessage());
        }
    }
}
