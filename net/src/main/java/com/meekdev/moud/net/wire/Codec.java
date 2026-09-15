package com.meekdev.moud.net.wire;

import com.meekdev.moud.core.clazz.ClassDef;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.clazz.PropertyType;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.net.replicate.Change;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class Codec {

    private static final int RESET = 0;
    private static final int CREATED = 1;
    private static final int MOVED = 2;
    private static final int DESTROYED = 3;
    private static final int TAGGED = 4;
    private static final int RENAMED = 5;

    private Codec() {}

    public static byte[] encode(List<Change> changes, InstanceTree tree, ClassRegistry classes) {
        Bytes out = new Bytes(128);

        List<Change> structure = new ArrayList<>();
        Map<Integer, ClassDef<?>> made = new HashMap<>();
        Map<Integer, Map<Integer, Object>> wrote = new LinkedHashMap<>();
        for (Change change : changes) {
            if (change instanceof Change.Created fresh) {
                ClassDef<?> def = classes.find(fresh.className());
                if (def != null) made.put(fresh.id(), def);
            }
            if (change instanceof Change.Wrote one) {
                wrote.computeIfAbsent(one.id(), id -> new TreeMap<>())
                        .put(one.property(), one.value());
            } else {
                structure.add(change);
            }
        }

        out.varint(structure.size());
        for (Change change : structure) {
            switch (change) {
                case Change.Reset ignored -> out.u8(RESET);
                case Change.Created fresh -> {
                    out.u8(CREATED);
                    out.zigzag(fresh.id());
                    out.text(fresh.className());
                    out.zigzag(fresh.parent());
                    out.text(fresh.name());
                }
                case Change.Moved moved -> {
                    out.u8(MOVED);
                    out.zigzag(moved.id());
                    out.zigzag(moved.parent());
                }
                case Change.Destroyed gone -> {
                    out.u8(DESTROYED);
                    out.zigzag(gone.id());
                }
                case Change.Tagged tagged -> {
                    out.u8(TAGGED);
                    out.zigzag(tagged.id());
                    out.text(tagged.tag());
                    out.u8(tagged.added() ? 1 : 0);
                }
                case Change.Renamed renamed -> {
                    out.u8(RENAMED);
                    out.zigzag(renamed.id());
                    out.text(renamed.name());
                }
                case Change.Wrote unreachable -> throw new IllegalStateException("partitioned");
            }
        }

        wrote.keySet().removeIf(id -> classOf(id, made, tree) == null);

        out.varint(wrote.size());
        for (Map.Entry<Integer, Map<Integer, Object>> entry : wrote.entrySet()) {
            int id = entry.getKey();
            out.zigzag(id);
            writeChanged(out, entry.getValue().keySet());
            ClassDef<?> def = classOf(id, made, tree);

            out.endFlags();
            for (Map.Entry<Integer, Object> one : entry.getValue().entrySet()) {
                if (typeOf(def, id, one.getKey()).isBool()) out.flag((Boolean) one.getValue());
            }
            out.endFlags();
            for (Map.Entry<Integer, Object> one : entry.getValue().entrySet()) {
                PropertyType type = typeOf(def, id, one.getKey());
                if (!type.isBool()) write(out, type, one.getValue());
            }
        }
        return out.toArray();
    }

    private static void writeChanged(Bytes out, Set<Integer> indices) {
        long mask = 0;
        int highest = 0;
        for (int index : indices) {
            mask |= 1L << index;
            highest = Math.max(highest, index);
        }
        int asList = varintLength(indices.size()) + indices.size();
        int asMask = 1 + varintLength(mask);
        if (asList <= asMask) {
            out.varint(indices.size());
            for (int index : indices) out.varint(index);
        } else {
            out.varint(0);
            out.varint(mask);
        }
    }

    private static List<Integer> readChanged(Bytes in) {
        int count = (int) in.readVarint();
        List<Integer> indices = new ArrayList<>(Math.max(1, count));
        if (count > 0) {
            for (int n = 0; n < count; n++) indices.add((int) in.readVarint());
            return indices;
        }
        long mask = in.readVarint();
        for (int index = 0; index < Long.SIZE; index++) {
            if ((mask & (1L << index)) != 0) indices.add(index);
        }
        return indices;
    }

    private static int varintLength(long value) {
        int bytes = 1;
        long left = value >>> 7;
        while (left != 0) {
            bytes++;
            left >>>= 7;
        }
        return bytes;
    }

    public static List<Change> decode(byte[] bytes, InstanceTree tree, ClassRegistry classes) {
        Bytes in = Bytes.reading(bytes);
        List<Change> changes = new ArrayList<>();
        Map<Integer, ClassDef<?>> pending = new HashMap<>();

        int structure = (int) in.readVarint();
        for (int n = 0; n < structure; n++) {
            int kind = in.readU8();
            switch (kind) {
                case RESET -> {
                    changes.add(new Change.Reset());
                    pending.clear();
                }
                case CREATED -> {
                    int id = (int) in.readZigzag();
                    String className = in.readText();
                    int parent = (int) in.readZigzag();
                    String name = in.readText();
                    ClassDef<?> def = classes.find(className);
                    if (def != null) pending.put(id, def);
                    changes.add(new Change.Created(id, className, parent, name));
                }
                case MOVED -> changes.add(
                        new Change.Moved((int) in.readZigzag(), (int) in.readZigzag()));
                case DESTROYED -> changes.add(new Change.Destroyed((int) in.readZigzag()));
                case TAGGED -> changes.add(new Change.Tagged((int) in.readZigzag(), in.readText(), in.readU8() == 1));
                case RENAMED -> changes.add(new Change.Renamed((int) in.readZigzag(), in.readText()));
                default -> throw new IllegalStateException("corrupt change kind " + kind);
            }
        }

        int instances = (int) in.readVarint();
        for (int n = 0; n < instances; n++) {
            int id = (int) in.readZigzag();
            ClassDef<?> def = classOf(id, pending, tree);

            List<PropertyDef> changed = new ArrayList<>();
            for (int index : readChanged(in)) {
                typeOf(def, id, index);
                changed.add(def.property(index));
            }

            in.endReadFlags();
            Map<PropertyDef, Object> values = new LinkedHashMap<>();
            for (PropertyDef property : changed) {
                if (property.type().isBool()) values.put(property, in.readFlag());
            }
            in.endReadFlags();
            for (PropertyDef property : changed) {
                if (!property.type().isBool()) values.put(property, read(in, property));
            }

            for (PropertyDef property : changed) {
                changes.add(new Change.Wrote(id, property.index(), values.get(property)));
            }
        }
        return changes;
    }

    private static ClassDef<?> classOf(int id, Map<Integer, ClassDef<?>> pending, InstanceTree tree) {
        ClassDef<?> fresh = pending.get(id);
        if (fresh != null) return fresh;
        Instance known = tree == null ? null : tree.byId(id);
        return known == null ? null : known.def();
    }

    private static PropertyType typeOf(ClassDef<?> def, int id, int index) {
        PropertyDef property = def == null ? null : def.property(index);
        if (property == null) {
            throw new IllegalStateException("unknown property " + index + " on instance " + id);
        }
        return property.type();
    }

    private static void write(Bytes out, PropertyType type, Object value) {
        switch (type) {
            case REF -> out.varint(value == null ? 0 : ((Integer) value) + 1L);
            case INT -> out.zigzag(((Number) value).intValue());
            case NUM -> out.f32((Double) value);
            case STRING, ASSET -> out.text((String) value);
            case VEC3 -> {
                Vector3 v = (Vector3) value;
                out.f32(v.x());
                out.f32(v.y());
                out.f32(v.z());
            }
            case QUAT -> Quats.write(out, (Quat) value);
            case CFRAME -> {
                CFrame frame = (CFrame) value;
                out.f32(frame.position().x());
                out.f32(frame.position().y());
                out.f32(frame.position().z());
                Quats.write(out, frame.rotation());
            }
            case COLOR -> {
                Color c = (Color) value;
                out.u8(Math.round(Math.clamp(c.r(), 0f, 1f) * 255f));
                out.u8(Math.round(Math.clamp(c.g(), 0f, 1f) * 255f));
                out.u8(Math.round(Math.clamp(c.b(), 0f, 1f) * 255f));
                out.u8(Math.round(Math.clamp(c.a(), 0f, 1f) * 255f));
            }
            case UDIM2 -> {
                UDim2 u = (UDim2) value;
                out.f32(u.xScale());
                out.f32(u.xOffset());
                out.f32(u.yScale());
                out.f32(u.yOffset());
            }
            case ENUM -> out.u8(((Enum<?>) value).ordinal());
            case BOOL -> throw new IllegalStateException("flags are written in the flag block");
        }
    }

    private static Object read(Bytes in, PropertyDef property) {
        return switch (property.type()) {
            case REF -> {
                long raw = in.readVarint();
                yield raw == 0 ? null : (int) (raw - 1);
            }
            case INT -> (int) in.readZigzag();
            case NUM -> in.readF32();
            case STRING, ASSET -> in.readText();
            case VEC3 -> new Vector3(in.readF32(), in.readF32(), in.readF32());
            case QUAT -> Quats.read(in);
            case CFRAME -> new CFrame(
                    new Vector3(in.readF32(), in.readF32(), in.readF32()), Quats.read(in));
            case COLOR -> new Color(in.readU8() / 255f, in.readU8() / 255f,
                    in.readU8() / 255f, in.readU8() / 255f);
            case UDIM2 -> new UDim2(in.readF32(), in.readF32(), in.readF32(), in.readF32());
            case ENUM -> option(property, in.readU8());
            case BOOL -> throw new IllegalStateException("flag read outside the flag block");
        };
    }

    private static Object option(PropertyDef property, int ordinal) {
        Object fallback = property.defaultValue();
        if (!(fallback instanceof Enum<?> one)) {
            throw new IllegalStateException(property.name() + ": enum property without a default");
        }
        Object[] all = one.getClass().getEnumConstants();
        if (ordinal < 0 || ordinal >= all.length) {
            throw new IllegalStateException(
                    property.name() + ": invalid enum ordinal " + ordinal);
        }
        return all[ordinal];
    }
}
