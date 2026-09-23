package com.meekdev.moud.script.host.tween;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Owners;
import com.meekdev.moud.core.tween.Keys;
import com.meekdev.moud.core.tween.PropertyTrack;
import com.meekdev.moud.core.tween.Timeline;
import com.meekdev.moud.core.ui.GuiLayout;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class TimelineLibrary {

    private static final PropertyDef KEYS = Classes.PROPERTY_TRACK.property("keys");

    private TimelineLibrary() {}

    public static void install(Host host) {
        host.api().alias("TimelineKey", "{ any }");
        Consumer<Double> step = dt -> {
            for (Timeline timeline : List.copyOf(host.world().tree().ofClass(Classes.TIMELINE))) {
                if (!timeline.isAlive() || Instance.dormant(timeline)) continue;
                boolean authoritative = !host.client() || timeline.id() < 0;
                try {
                    timeline.step(dt, authoritative, writes(host, timeline));
                } catch (RuntimeException e) {
                    host.error("timeline", e);
                }
            }
        };
        if (host.edits() == null) {
            if (host.client()) host.onRenderStep(step); else host.onStep(step);
        }

        Members timelines = host.instances().of(Classes.TIMELINE);
        timelines.method("play", "() -> ()", a -> {
            a.self(Timeline.class).play();
            return null;
        });
        timelines.method("pause", "() -> ()", a -> {
            a.self(Timeline.class).pause();
            return null;
        });
        timelines.method("stop", "() -> ()", a -> {
            a.self(Timeline.class).stop();
            return null;
        });
        timelines.method("addKey", "(target: Instance | string, property: string, time: number, value: any, shape: string?) -> PropertyTrack", a -> {
            Timeline timeline = a.self(Timeline.class);
            PropertyDef def = typed(timeline, a);
            Keys.Key key = key(def, a.number(3), a.get(4), a.string(5, ""));
            PropertyTrack track = track(host, timeline, a);
            write(host, track, Keys.write(Keys.with(track.keys(def.type()), key), def.type()));
            timeline.fit(key.time());
            return track;
        });
        timelines.method("setKeys", "(target: Instance | string, property: string, keys: { TimelineKey }) -> PropertyTrack", a -> {
            Timeline timeline = a.self(Timeline.class);
            PropertyDef def = typed(timeline, a);
            List<Keys.Key> keys = new ArrayList<>();
            for (Object entry : a.list(3)) {
                if (!(entry instanceof List<?> parts) || parts.size() < 2 || !(parts.get(0) instanceof Number time)) {
                    throw new HostError("setKeys expects keys like { time, value, shape? }");
                }
                Object shape = parts.size() > 2 ? parts.get(2) : "";
                if (!(shape instanceof String word)) throw new HostError("a key's shape is a string like \"cubic\" or \"quad-out\"");
                keys = Keys.with(keys, key(def, time.doubleValue(), parts.get(1), word));
            }
            PropertyTrack track = track(host, timeline, a);
            write(host, track, Keys.write(keys, def.type()));
            if (!keys.isEmpty()) timeline.fit(keys.getLast().time());
            return track;
        });
        timelines.method("addMarker", "(time: number, name: string) -> ()", a -> {
            Timeline timeline = a.self(Timeline.class);
            String name = a.string(2).trim();
            if (name.isEmpty() || name.contains(",")) throw new HostError("a marker needs a name without commas");
            host.instances().checkWrite(timeline, Classes.TIMELINE.property("markers"));
            timeline.addMarker(Math.max(0, a.number(1)), name);
            return null;
        });
        timelines.method("at", "(time: number, callback: () -> ()) -> ()", a -> {
            Timeline timeline = a.self(Timeline.class);
            Callable callback = a.callable(2).retain();
            Runnable forget = timeline.at(Math.max(0, a.number(1)), () -> host.call(callback, "timeline.at"));
            host.ownership().onRelease(host.ownership().current(), () -> {
                forget.run();
                callback.release();
            });
            return null;
        });

        Members tracks = host.instances().of(Classes.PROPERTY_TRACK);
        tracks.method("getKeys", "() -> { TimelineKey }", a -> {
            PropertyTrack track = a.self(PropertyTrack.class);
            Instance target = track.resolve();
            PropertyDef def = target == null ? null : track.propertyOf(target);
            if (def == null) throw new HostError("%s does not point at a property that can be animated", track.name());
            List<Object> out = new ArrayList<>();
            for (Keys.Key key : track.keys(def.type())) {
                List<Object> entry = new ArrayList<>(List.of(key.time(), Keys.value(def.type(), key.value())));
                String shape = key.shape().write();
                if (!shape.isEmpty()) entry.add(shape);
                out.add(entry);
            }
            return out;
        });
    }

    private static Predicate<Instance> writes(Host host, Timeline timeline) {
        if (!host.client()) return instance -> true;
        boolean local = timeline.id() < 0;
        return instance -> instance.id() < 0 || GuiLayout.interfacePart(instance) || local && Owners.owns(host.me(), instance);
    }

    private static PropertyTrack track(Host host, Timeline timeline, Args a) {
        Object target = a.get(1);
        String property = a.string(2);
        for (PropertyTrack track : timeline.tracks()) {
            boolean same = target instanceof Instance instance ? track.target == instance : track.target == null && track.path.equals(target);
            if (same && track.property.equals(property)) return track;
        }
        host.instances().checkWrite(timeline, Classes.TIMELINE.property("length"));
        String name = (target instanceof Instance instance ? instance.name() : (String) target) + "." + property;
        return Instances.create(Classes.PROPERTY_TRACK, timeline, name, track -> {
            if (target instanceof Instance instance) track.target = instance;
            else track.path = (String) target;
            track.property = property;
        });
    }

    private static PropertyDef typed(Timeline timeline, Args a) {
        Object path = a.get(1);
        Instance target = path instanceof Instance instance ? instance
                : path instanceof String text ? PropertyTrack.find(timeline.parent(), text) : null;
        if (!(path instanceof Instance) && !(path instanceof String)) throw new HostError("a track's target is an instance or a path");
        if (target == null) throw new HostError("there is nothing at '%s' to animate", path);
        String property = a.string(2);
        PropertyDef def = target.def().property(property);
        if (def == null) throw new HostError("%s has no property '%s'", target.def().name(), property);
        if (def.readOnly()) throw new HostError("%s.%s is read-only", target.def().name(), def.name());
        if (!Keys.animatable(def.type())) throw new HostError("%s.%s cannot be animated", target.def().name(), def.name());
        return def;
    }

    private static Keys.Key key(PropertyDef def, double time, Object value, String shape) {
        try {
            return new Keys.Key(Math.max(0, time), Keys.components(def.type(), value), Keys.Shape.parse(shape));
        } catch (IllegalArgumentException e) {
            throw new HostError("%s: %s", def.name(), e.getMessage());
        }
    }

    private static void write(Host host, PropertyTrack track, String keys) {
        host.instances().checkWrite(track, KEYS);
        Instances.setObj(track, KEYS, keys);
    }
}
