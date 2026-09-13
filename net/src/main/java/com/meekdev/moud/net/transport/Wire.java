package com.meekdev.moud.net.transport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Wire {

    public static final int MAX_ARGS = 16;

    public static final int MAX_DEPTH = 8;

    public static final int MAX_VALUES = 256;

    private Wire() {}

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
                || what instanceof Vector3 || what instanceof Quat || what instanceof CFrame
                || what instanceof Color || what instanceof UDim2) {
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
