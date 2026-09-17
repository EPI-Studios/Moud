package com.meekdev.moud.net.wire;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;

final class Attribute {

    private static final int NIL = 0;
    private static final int BOOL = 1;
    private static final int NUMBER = 2;
    private static final int STRING = 3;
    private static final int VEC3 = 4;
    private static final int COLOR = 5;
    private static final int CFRAME = 6;
    private static final int UDIM2 = 7;

    private Attribute() {}

    static void write(Bytes out, Object value) {
        switch (value) {
            case null -> out.u8(NIL);
            case Boolean b -> {
                out.u8(BOOL);
                out.u8(b ? 1 : 0);
            }
            case Number n -> {
                out.u8(NUMBER);
                out.f64(n.doubleValue());
            }
            case String s -> {
                out.u8(STRING);
                out.text(s);
            }
            case Vector3 v -> {
                out.u8(VEC3);
                vector(out, v);
            }
            case Color c -> {
                out.u8(COLOR);
                out.f32(c.r());
                out.f32(c.g());
                out.f32(c.b());
                out.f32(c.a());
            }
            case CFrame c -> {
                out.u8(CFRAME);
                vector(out, c.position());
                Quats.write(out, c.rotation());
            }
            case UDim2 u -> {
                out.u8(UDIM2);
                out.f32(u.xScale());
                out.f32(u.xOffset());
                out.f32(u.yScale());
                out.f32(u.yOffset());
            }
            default -> throw new IllegalArgumentException("an attribute cannot hold a " + value.getClass().getSimpleName());
        }
    }

    static Object read(Bytes in) {
        int kind = in.readU8();
        return switch (kind) {
            case NIL -> null;
            case BOOL -> in.readU8() == 1;
            case NUMBER -> in.readF64();
            case STRING -> in.readText();
            case VEC3 -> new Vector3(in.readF32(), in.readF32(), in.readF32());
            case COLOR -> new Color((float) in.readF32(), (float) in.readF32(), (float) in.readF32(), (float) in.readF32());
            case CFRAME -> new CFrame(new Vector3(in.readF32(), in.readF32(), in.readF32()), Quats.read(in));
            case UDIM2 -> new UDim2(in.readF32(), in.readF32(), in.readF32(), in.readF32());
            default -> throw new IllegalStateException("corrupt attribute kind " + kind);
        };
    }

    private static void vector(Bytes out, Vector3 v) {
        out.f32(v.x());
        out.f32(v.y());
        out.f32(v.z());
    }
}
