package com.meekdev.moud.net.transport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// what may cross the boundary, and what happens to what may not
//
// numbers, text, flags, nothing, the math types, an instance, and tables of those. anything else is
// refused by name rather than turned into nil on the far side, which is a bug that ships because
// neither side is ever told
public final class Wire {

    // sixteen is more than anything sane sends and it bounds the work a hostile client can ask for
    public static final int MAX_ARGS = 16;

    // a table inside a table inside a table is already a design smell; eight is far past it
    public static final int MAX_DEPTH = 8;

    // total values in one delivery, counting into tables. a payload is gameplay, not a file
    public static final int MAX_VALUES = 256;

    private Wire() {}

    // an instance crosses as the id it is, resolved on the far side, which is what a REF property
    // already does. a local instance has a negative id and does not exist over there, so sending one
    // is a mistake rather than a nil
    public record Ref(int id) {}

    public static List<Object> pack(List<Object> args) {
        if (args.size() > MAX_ARGS) {
            throw new IllegalArgumentException("a remote takes at most " + MAX_ARGS
                    + " arguments and was given " + args.size());
        }
        Count count = new Count();
        List<Object> packed = new ArrayList<>(args.size());
        for (int n = 0; n < args.size(); n++) {
            packed.add(value(args.get(n), 0, count, "argument " + (n + 1)));
        }
        return packed;
    }

    // the far side's copy. a table arrives as a new table: the identity does not cross, only the shape
    // and the contents
    public static List<Object> unpack(List<Object> packed, InstanceTree into) {
        List<Object> args = new ArrayList<>(packed.size());
        for (Object value : packed) args.add(resolve(value, into));
        return args;
    }

    private static Object value(Object what, int depth, Count count, String where) {
        if (++count.seen > MAX_VALUES) {
            throw new IllegalArgumentException("a remote carries at most " + MAX_VALUES + " values");
        }
        if (what == null) return null;
        if (what instanceof Double || what instanceof Boolean || what instanceof String
                || what instanceof Vec3 || what instanceof Quat || what instanceof CFrame
                || what instanceof Color) {
            return what;
        }
        if (what instanceof Integer n) return n.doubleValue();
        if (what instanceof Instance instance) {
            if (!instance.isAlive()) {
                throw new IllegalArgumentException(where + " is a destroyed instance");
            }
            if (instance.id() < 0) {
                throw new IllegalArgumentException(where + " is " + instance
                        + ", which is local: it was made on one side and does not exist on the other."
                        + " send something the two sides share, or send its name");
            }
            return new Ref(instance.id());
        }
        if (what instanceof List<?> list) {
            if (depth >= MAX_DEPTH) {
                throw new IllegalArgumentException(where + " nests deeper than " + MAX_DEPTH);
            }
            List<Object> copy = new ArrayList<>(list.size());
            for (int n = 0; n < list.size(); n++) {
                copy.add(value(list.get(n), depth + 1, count, where + "[" + (n + 1) + "]"));
            }
            return copy;
        }
        if (what instanceof Map<?, ?> map) {
            if (depth >= MAX_DEPTH) {
                throw new IllegalArgumentException(where + " nests deeper than " + MAX_DEPTH);
            }
            Map<String, Object> copy = new LinkedHashMap<>(map.size() * 2);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                // coercing the key to text would turn one table into a different one without saying so
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException(where + " has a key that is not text: "
                            + entry.getKey() + ". a table that crosses is a list or is keyed by text");
                }
                copy.put(key, value(entry.getValue(), depth + 1, count, where + "." + key));
            }
            return copy;
        }
        throw new IllegalArgumentException(where + " is a " + what.getClass().getSimpleName()
                + ", which cannot cross. what can: a number, text, a flag, nothing, a vector, a"
                + " rotation, a frame, a colour, an instance, and lists or text keyed tables of those");
    }

    private static Object resolve(Object what, InstanceTree into) {
        if (what instanceof Ref ref) {
            // gone by the time it landed is not an error: it is the ordinary race between one side
            // destroying something and the other hearing about it
            return into == null ? null : into.byId(ref.id());
        }
        if (what instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object value : list) out.add(resolve(value, into));
            return out;
        }
        if (what instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size() * 2);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put((String) entry.getKey(), resolve(entry.getValue(), into));
            }
            return out;
        }
        return what;
    }

    private static final class Count {
        private int seen;
    }
}
