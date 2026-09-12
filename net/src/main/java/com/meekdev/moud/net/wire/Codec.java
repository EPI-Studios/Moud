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
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.net.replicate.Change;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// a tick's changes, as bytes and back, in the shape §10.2 asks for
//
// structure ahead of properties in the same packet, so a client is never handed a property for an
// instance it has not been told about. then one section per instance that changed: its id, its dirty
// mask, and the values the mask names -- which is the same bitmask the tree already keeps, sent as a
// varint so the ordinary delta of one or two low numbered properties costs a single byte
//
// this runs in a solo game too. §10.2 is explicit about it and it is the right call: a bug that only
// appears once the bytes are real is one nobody finds until the first time two people play together,
// which is far too late to be discovering the codec
public final class Codec {

    private static final int RESET = 0;
    private static final int CREATED = 1;
    private static final int MOVED = 2;
    private static final int DESTROYED = 3;

    private Codec() {}

    // the tree it is being sent from, for the same reason decode takes the one it is going to: a
    // property's type is a fact about its class, and INT and REF both arrive here as an Integer. one
    // of the two would be written wrong if the java type were what decided
    public static byte[] encode(List<Change> changes, InstanceTree tree, ClassRegistry classes) {
        Bytes out = new Bytes(128);

        List<Change> structure = new ArrayList<>();
        Map<Integer, ClassDef<?>> made = new HashMap<>();
        // grouped by instance and ordered by property index, because that is what a mask is: a set,
        // read back in one order
        Map<Integer, Map<Integer, Object>> wrote = new java.util.LinkedHashMap<>();
        for (Change change : changes) {
            if (change instanceof Change.Created fresh) {
                ClassDef<?> def = classes.find(fresh.className());
                if (def != null) made.put(fresh.id(), def);
            }
            if (change instanceof Change.Wrote one) {
                wrote.computeIfAbsent(one.id(), id -> new java.util.TreeMap<>())
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
                case Change.Wrote unreachable -> throw new IllegalStateException("partitioned");
            }
        }

        // an instance this side cannot name a class for has been destroyed since the write was
        // recorded, which is a race and not a fault: the recorder saw it change and then it went. the
        // far side would have nothing to apply it to either way
        wrote.keySet().removeIf(id -> classOf(id, made, tree) == null);

        out.varint(wrote.size());
        for (Map.Entry<Integer, Map<Integer, Object>> entry : wrote.entrySet()) {
            int id = entry.getKey();
            out.zigzag(id);
            writeChanged(out, entry.getValue().keySet());
            ClassDef<?> def = classOf(id, made, tree);

            // every flag first, in one block, then everything else. a flag is a bit and a byte holds
            // eight of them
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

    // which properties changed: either their indices, or the bitmask, whichever is fewer bytes
    //
    // §10.2 asks for the dirty bitmask, and a mask is the right answer when the indices are low or
    // numerous -- one byte covers the first seven. it is the wrong answer when they are neither: a
    // varint mask costs a byte per seven indices, so one property at index thirty seven costs six
    // bytes of mask to say one thing. a character has thirty eight properties and the ones that
    // change every tick are spread across them
    //
    // so both, chosen per instance, and the count says which: nothing means a mask follows
    private static void writeChanged(Bytes out, java.util.Set<Integer> indices) {
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

    // the tree is what says which class an instance is, and therefore what type each property is. an
    // instance created in this same packet is not in it yet, so the classes named here are remembered
    // as the structure section is read
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
                default -> throw new IllegalStateException("a change of kind " + kind + " is corrupt");
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

            // the same order the writer used: flags out of the block, then the rest
            in.endReadFlags();
            Map<PropertyDef, Object> values = new java.util.LinkedHashMap<>();
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
            throw new IllegalStateException("property " + index + " of instance " + id
                    + " is not one this side has. the two sides disagree about its class");
        }
        return property.type();
    }

    private static void write(Bytes out, PropertyType type, Object value) {
        switch (type) {
            // nothing is zero, and an id is never zero: the tree's counter starts at one and a local
            // instance is negative
            case REF -> out.varint(value == null ? 0 : ((Integer) value) + 1L);
            case INT -> out.zigzag((Integer) value);
            case NUM -> out.f32((Double) value);
            case STRING, ASSET -> out.text((String) value);
            case VEC3 -> {
                Vec3 v = (Vec3) value;
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
            case ENUM -> out.u8(((Enum<?>) value).ordinal());
            case BOOL -> throw new IllegalStateException("a flag goes in the block, not here");
        }
    }

    private static Object read(Bytes in, PropertyDef property) {
        return switch (property.type()) {
            // a reference is the id it points at, and zero is nothing: an id is never zero because
            // the tree's counter starts at one and a local instance is negative
            case REF -> {
                long raw = in.readVarint();
                yield raw == 0 ? null : (int) (raw - 1);
            }
            case INT -> (int) in.readZigzag();
            case NUM -> in.readF32();
            case STRING, ASSET -> in.readText();
            case VEC3 -> new Vec3(in.readF32(), in.readF32(), in.readF32());
            case QUAT -> Quats.read(in);
            case CFRAME -> new CFrame(
                    new Vec3(in.readF32(), in.readF32(), in.readF32()), Quats.read(in));
            case COLOR -> new Color(in.readU8() / 255f, in.readU8() / 255f,
                    in.readU8() / 255f, in.readU8() / 255f);
            case ENUM -> option(property, in.readU8());
            case BOOL -> throw new IllegalStateException("a flag comes out of the block, not here");
        };
    }

    // the constants of whichever enum the property holds, which its own default is an instance of
    private static Object option(PropertyDef property, int ordinal) {
        Object fallback = property.defaultValue();
        if (!(fallback instanceof Enum<?> one)) {
            throw new IllegalStateException(property.name() + " is an enum with no default to read");
        }
        Object[] all = one.getClass().getEnumConstants();
        if (ordinal < 0 || ordinal >= all.length) {
            throw new IllegalStateException(
                    property.name() + " has no option " + ordinal + ", so the two sides differ");
        }
        return all[ordinal];
    }
}
