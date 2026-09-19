package com.meekdev.moud.core.render;

import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PresetBlend {

    public interface Writer {
        void write(Instance target, PropertyDef property, Object value);
    }

    public static final Writer DIRECT = (target, property, value) -> {
        switch (property.type()) {
            case BOOL -> Instances.setBool(target, property, (Boolean) value);
            case INT, NUM -> Instances.setNum(target, property, ((Number) value).doubleValue());
            default -> Instances.setObj(target, property, value);
        }
    };

    private enum When { START, END, ALONG }

    private record Track(Instance target, PropertyDef property, Object from, Object to, When when) {}

    private final List<Track> tracks;
    private final double seconds;
    private double elapsed;
    private boolean begun;

    private PresetBlend(List<Track> tracks, double seconds) {
        this.tracks = tracks;
        this.seconds = seconds;
    }

    public static PresetBlend of(Preset preset, List<? extends Instance> targets, double seconds) {
        double span = Double.isFinite(seconds) ? Math.max(0, seconds) : 0;
        List<Track> tracks = new ArrayList<>();
        for (Instance target : targets) {
            if (target == null) continue;
            boolean weather = target instanceof Weather;
            if (weather) {
                PropertyDef transition = target.def().property("transition");
                tracks.add(new Track(target, transition, transition.getNum(target), span, When.START));
            }
            for (Map.Entry<String, Object> entry : preset.of(target.def()).entrySet()) {
                PropertyDef property = target.def().property(entry.getKey());
                if (property == null) continue;
                Object from = read(target, property);
                Object to = entry.getValue();
                tracks.add(new Track(target, property, from, to, weather ? When.START : when(property, to)));
            }
        }
        return new PresetBlend(tracks, span);
    }

    public static void apply(Preset preset, List<? extends Instance> targets, Writer writer) {
        of(preset, targets, 0).step(0, writer);
    }

    public boolean step(double dt, Writer writer) {
        if (begun) elapsed += Math.max(0, dt);
        begun = true;
        double t = seconds <= 0 ? 1 : Math.clamp(elapsed / seconds, 0, 1);
        double eased = t * t * (3 - 2 * t);
        for (Track track : tracks) {
            if (!track.target().isAlive()) continue;
            Object value = switch (track.when()) {
                case START -> track.to();
                case END -> t >= 1 ? track.to() : track.from();
                case ALONG -> between(track.property(), track.from(), track.to(), eased);
            };
            writer.write(track.target(), track.property(), value);
        }
        return t >= 1;
    }

    public boolean finished() {
        return begun && (seconds <= 0 || elapsed >= seconds);
    }

    public boolean touches(Instance instance) {
        for (Track track : tracks) {
            if (track.target() == instance) return true;
        }
        return false;
    }

    static Object between(PropertyDef property, Object from, Object to, double t) {
        if (from instanceof Number a && to instanceof Number b) {
            double start = a.doubleValue();
            double end = b.doubleValue();
            if (property.name().equals("clockTime")) return Daylight.hours(start + shortest(start, end) * t);
            double value = start + (end - start) * t;
            return property.type() == PropertyType.INT ? (double) Math.round(value) : value;
        }
        if (from instanceof Color a && to instanceof Color b) return a.lerp(b, (float) t);
        if (from instanceof Vector3 a && to instanceof Vector3 b) return a.lerp(b, t);
        return t >= 1 ? to : from;
    }

    static double shortest(double from, double to) {
        double turn = Daylight.hours(to - from);
        return turn > 12 ? turn - 24 : turn;
    }

    private static When when(PropertyDef property, Object to) {
        return switch (property.type()) {
            case NUM, INT, COLOR, VEC3 -> When.ALONG;
            case BOOL -> Boolean.TRUE.equals(to) ? When.START : When.END;
            default -> When.START;
        };
    }

    private static Object read(Instance target, PropertyDef property) {
        return switch (property.type()) {
            case BOOL -> property.getBool(target);
            case INT, NUM -> property.getNum(target);
            default -> property.getObj(target);
        };
    }
}
