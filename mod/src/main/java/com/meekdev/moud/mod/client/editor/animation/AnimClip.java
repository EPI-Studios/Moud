package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.character.Clip;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class AnimClip {

    public static final List<String> PRIORITIES = List.of("core", "idle", "movement", "action", "action2", "action3", "action4");
    public static final double EPSILON = 1e-6;

    public enum Loop {
        LOOP("loop", "Loop"), ONCE("once", "Once"), HOLD("hold", "Hold");

        private final String key;
        private final String label;

        Loop(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public static Loop named(String key) {
            for (Loop loop : values()) {
                if (loop.key.equals(key)) return loop;
            }
            throw new IllegalArgumentException("loop must be loop, once or hold, got " + key);
        }
    }

    public enum Space {
        BODY("body"), VIEW("view");

        private final String key;

        Space(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Space named(String key) {
            for (Space space : values()) {
                if (space.key.equals(key)) return space;
            }
            throw new IllegalArgumentException("space must be body or view, got " + key);
        }
    }

    public enum Blend {
        NORMAL("normal", "Normal"), ADDITIVE("additive", "Additive");

        private final String key;
        private final String label;

        Blend(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public static Blend named(String key) {
            for (Blend blend : values()) {
                if (blend.key.equals(key)) return blend;
            }
            throw new IllegalArgumentException("blend must be normal or additive, got " + key);
        }
    }

    public enum Side {
        SERVER("server", "Server"), CLIENT("client", "Client"), BOTH("both", "Both");

        private final String key;
        private final String label;

        Side(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public static Side named(String key) {
            for (Side side : values()) {
                if (side.key.equals(key)) return side;
            }
            throw new IllegalArgumentException("an event fires on server, client or both, got " + key);
        }

        public Clip.Side runtime() {
            return switch (this) {
                case SERVER -> Clip.Side.SERVER;
                case CLIENT -> Clip.Side.CLIENT;
                case BOTH -> Clip.Side.BOTH;
            };
        }
    }

    public record Marker(double time, String name, String value) {
        public Marker at(double moved) {
            return new Marker(moved, name, value);
        }
    }

    public record Event(double time, String name, Side on, Object payload, String sound, String particle, boolean preview) {

        public Event {
            if (payload == null) payload = Map.of();
            if (payload instanceof Map<?, ?> fields) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> field : fields.entrySet()) copy.put(String.valueOf(field.getKey()), field.getValue() == null ? "" : field.getValue());
                payload = Collections.unmodifiableMap(copy);
            }
        }

        public boolean table() {
            return payload instanceof Map<?, ?>;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> fields() {
            return payload instanceof Map<?, ?> fields ? (Map<String, Object>) fields : Map.of();
        }

        public Event at(double moved) {
            return new Event(moved, name, on, payload, sound, particle, preview);
        }

        public Event named(String changed) {
            return new Event(time, changed, on, payload, sound, particle, preview);
        }

        public Event firing(Side changed) {
            return new Event(time, name, changed, payload, sound, particle, preview);
        }

        public Event carrying(Object changed) {
            return new Event(time, name, on, changed, sound, particle, preview);
        }

        public Event previewing(String changedSound, String changedParticle, boolean on) {
            return new Event(time, name, this.on, payload, changedSound, changedParticle, on);
        }
    }

    public record ViewModel(String model) {
        public static final ViewModel DEFAULT = new ViewModel("");
    }

    public double length = 1.0;
    public int fps = 20;
    public Loop loop = Loop.ONCE;
    public String priority = "action";
    public String rig = "player";
    public Space space = Space.BODY;
    public Blend blend = Blend.NORMAL;
    public List<String> mask = new ArrayList<>();
    public String euler = Clip.EULER;
    public final Map<String, Map<Channel, List<AnimKey>>> channels = new LinkedHashMap<>();
    public final Map<String, Double> weights = new LinkedHashMap<>();
    public final Map<String, Clip.Link> skeleton = new LinkedHashMap<>();
    public final Map<String, String> retarget = new LinkedHashMap<>();
    public final List<Marker> markers = new ArrayList<>();
    public final List<Event> events = new ArrayList<>();
    public ViewModel view = ViewModel.DEFAULT;

    public AnimClip copy() {
        AnimClip copy = new AnimClip();
        copy.length = length;
        copy.fps = fps;
        copy.loop = loop;
        copy.priority = priority;
        copy.rig = rig;
        copy.space = space;
        copy.blend = blend;
        copy.mask = new ArrayList<>(mask);
        copy.euler = euler;
        copy.weights.putAll(weights);
        copy.skeleton.putAll(skeleton);
        copy.retarget.putAll(retarget);
        for (Map.Entry<String, Map<Channel, List<AnimKey>>> joint : channels.entrySet()) {
            Map<Channel, List<AnimKey>> tracks = new EnumMap<>(Channel.class);
            for (Map.Entry<Channel, List<AnimKey>> track : joint.getValue().entrySet()) tracks.put(track.getKey(), new ArrayList<>(track.getValue()));
            copy.channels.put(joint.getKey(), tracks);
        }
        copy.markers.addAll(markers);
        copy.events.addAll(events);
        copy.view = view;
        return copy;
    }

    public List<AnimKey> keys(String joint, Channel channel) {
        Map<Channel, List<AnimKey>> tracks = channels.get(joint);
        if (tracks == null) return List.of();
        List<AnimKey> keys = tracks.get(channel);
        return keys == null ? List.of() : keys;
    }

    public List<AnimKey> track(String joint, Channel channel) {
        return channels.computeIfAbsent(joint, name -> new EnumMap<>(Channel.class)).computeIfAbsent(channel, ignored -> new ArrayList<>());
    }

    public int keyCount(String joint) {
        Map<Channel, List<AnimKey>> tracks = channels.get(joint);
        if (tracks == null) return 0;
        int count = 0;
        for (List<AnimKey> keys : tracks.values()) count += keys.size();
        return count;
    }

    public @Nullable AnimKey keyAt(String joint, Channel channel, double time) {
        for (AnimKey key : keys(joint, channel)) {
            if (Math.abs(key.time() - time) < EPSILON) return key;
        }
        return null;
    }

    public void put(String joint, Channel channel, AnimKey key) {
        List<AnimKey> keys = track(joint, channel);
        AnimKey placed = key.at(KeyRef.clean(Math.max(0, key.time())));
        keys.removeIf(existing -> Math.abs(existing.time() - placed.time()) < EPSILON);
        keys.add(placed);
        keys.sort(Comparator.comparingDouble(AnimKey::time));
    }

    public boolean remove(String joint, Channel channel, double time) {
        Map<Channel, List<AnimKey>> tracks = channels.get(joint);
        if (tracks == null) return false;
        List<AnimKey> keys = tracks.get(channel);
        if (keys == null) return false;
        boolean removed = keys.removeIf(existing -> Math.abs(existing.time() - time) < EPSILON);
        if (keys.isEmpty()) tracks.remove(channel);
        if (tracks.isEmpty()) channels.remove(joint);
        return removed;
    }

    public void prune() {
        channels.values().forEach(tracks -> tracks.values().removeIf(List::isEmpty));
        channels.values().removeIf(Map::isEmpty);
    }

    public double lastKeyTime() {
        double last = 0;
        for (Map<Channel, List<AnimKey>> tracks : channels.values()) {
            for (List<AnimKey> keys : tracks.values()) {
                if (!keys.isEmpty()) last = Math.max(last, keys.getLast().time());
            }
        }
        return last;
    }

    public double snap(double time) {
        if (fps <= 0) return time;
        return Math.round(time * fps) / (double) fps;
    }

    public void sortTimed() {
        markers.sort(Comparator.comparingDouble(Marker::time));
        events.sort(Comparator.comparingDouble(Event::time));
    }
}
