package com.meekdev.moud.net.wire;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.net.transport.Wire;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Args {

    private static final int NIL = 0;
    private static final int FALSE = 1;
    private static final int TRUE = 2;
    private static final int INT = 3;
    private static final int NUM = 4;
    private static final int TEXT = 5;
    private static final int VEC3 = 6;
    private static final int QUAT = 7;
    private static final int CFRAME = 8;
    private static final int COLOR = 9;
    private static final int REF = 10;
    private static final int LIST = 11;
    private static final int MAP = 12;
    private static final int UDIM2 = 13;

    private static final double EXACT = 9007199254740992.0;

    private Args() {}

    public static byte[] encode(List<Object> packed) {
        Bytes out = new Bytes(64);
        out.varint(packed.size());
        for (Object value : packed) write(out, value);
        return out.toArray();
    }

    public static List<Object> decode(byte[] bytes) {
        Bytes in = Bytes.reading(bytes);
        int count = (int) in.readVarint();
        if (count > Wire.MAX_ARGS) {
            throw new IllegalArgumentException("a delivery claiming " + count
                    + " arguments, past the " + Wire.MAX_ARGS + " a channel takes");
        }
        Count seen = new Count();
        List<Object> args = new ArrayList<>(count);
        for (int n = 0; n < count; n++) args.add(read(in, 0, seen));
        return args;
    }

    private static void write(Bytes out, Object value) {
        switch (value) {
            case null -> out.u8(NIL);
            case Boolean flag -> out.u8(flag ? TRUE : FALSE);
            case Double number -> {
                if (number == Math.rint(number) && Math.abs(number) < EXACT
                        && !number.isInfinite()) {
                    out.u8(INT);
                    out.zigzag((long) (double) number);
                } else {
                    out.u8(NUM);
                    out.f64(number);
                }
            }
            case String text -> {
                out.u8(TEXT);
                out.text(text);
            }
            case Vec3 v -> {
                out.u8(VEC3);
                position(out, v);
            }
            case Quat q -> {
                out.u8(QUAT);
                Quats.write(out, q);
            }
            case CFrame frame -> {
                out.u8(CFRAME);
                position(out, frame.position());
                Quats.write(out, frame.rotation());
            }
            case Color c -> {
                out.u8(COLOR);
                out.u8(Math.round(Math.clamp(c.r(), 0, 1) * 255));
                out.u8(Math.round(Math.clamp(c.g(), 0, 1) * 255));
                out.u8(Math.round(Math.clamp(c.b(), 0, 1) * 255));
                out.u8(Math.round(Math.clamp(c.a(), 0, 1) * 255));
            }
            case UDim2 u -> {
                out.u8(UDIM2);
                out.f32(u.xScale());
                out.f32(u.xOffset());
                out.f32(u.yScale());
                out.f32(u.yOffset());
            }
            case Wire.Ref ref -> {
                out.u8(REF);
                out.varint(ref.id());
            }
            case List<?> list -> {
                out.u8(LIST);
                out.varint(list.size());
                for (Object one : list) write(out, one);
            }
            case Map<?, ?> map -> {
                out.u8(MAP);
                out.varint(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    out.text((String) entry.getKey());
                    write(out, entry.getValue());
                }
            }
            default -> throw new IllegalArgumentException(
                    value.getClass().getSimpleName() + " got past the pack");
        }
    }

    private static void position(Bytes out, Vec3 v) {
        out.f32(v.x());
        out.f32(v.y());
        out.f32(v.z());
    }

    private static Object read(Bytes in, int depth, Count seen) {
        if (++seen.values > Wire.MAX_VALUES) {
            throw new IllegalArgumentException("a delivery carrying past the "
                    + Wire.MAX_VALUES + " values a channel takes");
        }
        int tag = in.readU8();
        return switch (tag) {
            case NIL -> null;
            case FALSE -> Boolean.FALSE;
            case TRUE -> Boolean.TRUE;
            case INT -> (double) in.readZigzag();
            case NUM -> in.readF64();
            case TEXT -> in.readText();
            case VEC3 -> new Vec3(in.readF32(), in.readF32(), in.readF32());
            case QUAT -> Quats.read(in);
            case CFRAME -> new CFrame(
                    new Vec3(in.readF32(), in.readF32(), in.readF32()), Quats.read(in));
            case COLOR -> new Color(in.readU8() / 255f, in.readU8() / 255f,
                    in.readU8() / 255f, in.readU8() / 255f);
            case REF -> new Wire.Ref((int) in.readVarint());
            case UDIM2 -> new UDim2(in.readF32(), in.readF32(), in.readF32(), in.readF32());
            case LIST -> {
                int size = nested(in, depth, seen);
                List<Object> list = new ArrayList<>(size);
                for (int n = 0; n < size; n++) list.add(read(in, depth + 1, seen));
                yield list;
            }
            case MAP -> {
                int size = nested(in, depth, seen);
                Map<String, Object> map = new LinkedHashMap<>(size * 2);
                for (int n = 0; n < size; n++) {
                    map.put(in.readText(), read(in, depth + 1, seen));
                }
                yield map;
            }
            default -> throw new IllegalArgumentException(tag + " is not a kind of value");
        };
    }

    private static int nested(Bytes in, int depth, Count seen) {
        if (depth >= Wire.MAX_DEPTH) {
            throw new IllegalArgumentException("a delivery nesting past " + Wire.MAX_DEPTH);
        }
        int size = (int) in.readVarint();
        if (size < 0 || size > Wire.MAX_VALUES - seen.values) {
            throw new IllegalArgumentException("a table claiming " + size + " in it");
        }
        return size;
    }

    private static final class Count {
        private int values;
    }
}
