package com.meekdev.moud.mod.client.editor.document;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.net.replicate.Change;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class PendingEdits {

    private static final long GIVE_UP_NANOS = 1_500_000_000L;
    private static final double TOLERANCE = 1.0e-4;

    private record Key(int id, int property) {}

    private record Sent(@Nullable Object value, long at) {}

    private static final Map<Key, Sent> PENDING = new HashMap<>();

    private PendingEdits() {}

    static void sent(int id, int property, @Nullable Object value) {
        PENDING.put(new Key(id, property), new Sent(value, System.nanoTime()));
    }

    public static boolean accept(Change change) {
        if (change instanceof Change.Reset) {
            PENDING.clear();
            return true;
        }
        if (PENDING.isEmpty()) return true;
        if (change instanceof Change.Destroyed destroyed) {
            PENDING.keySet().removeIf(key -> key.id() == destroyed.id());
            return true;
        }
        if (!(change instanceof Change.Wrote wrote)) return true;
        Key key = new Key(wrote.id(), wrote.property());
        Sent sent = PENDING.get(key);
        if (sent == null) return true;
        if (same(sent.value(), wrote.value())) {
            PENDING.remove(key);
            return true;
        }
        if (System.nanoTime() - sent.at() > GIVE_UP_NANOS) {
            PENDING.remove(key);
            return true;
        }
        return false;
    }

    public static boolean same(@Nullable Object a, @Nullable Object b) {
        return switch (a) {
            case Number x when b instanceof Number y -> Math.abs(x.doubleValue() - y.doubleValue()) <= TOLERANCE;
            case Vector3 x when b instanceof Vector3 y -> x.distance(y) <= TOLERANCE;
            case Quat x when b instanceof Quat y -> Math.abs(x.x() * y.x() + x.y() * y.y() + x.z() * y.z() + x.w() * y.w()) >= 1.0 - TOLERANCE;
            case CFrame x when b instanceof CFrame y -> same(x.position(), y.position()) && same(x.rotation(), y.rotation());
            case Color x when b instanceof Color y -> Math.abs(x.r() - y.r()) + Math.abs(x.g() - y.g()) + Math.abs(x.b() - y.b()) + Math.abs(x.a() - y.a()) <= 0.02;
            case UDim2 x when b instanceof UDim2 y -> Math.abs(x.xScale() - y.xScale()) + Math.abs(x.xOffset() - y.xOffset())
                    + Math.abs(x.yScale() - y.yScale()) + Math.abs(x.yOffset() - y.yOffset()) <= TOLERANCE * 4;
            case null, default -> Objects.equals(a, b);
        };
    }
}
