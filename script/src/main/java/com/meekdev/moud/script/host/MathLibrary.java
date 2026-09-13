package com.meekdev.moud.script.host;

import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MathLibrary {

    private static final int[] PERMUTATION = new int[512];

    static {
        int[] base = {151, 160, 137, 91, 90, 15, 131, 13, 201, 95, 96, 53, 194, 233, 7, 225, 140, 36, 103, 30, 69, 142, 8, 99,
            37, 240, 21, 10, 23, 190, 6, 148, 247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32, 57, 177, 33,
            88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175, 74, 165, 71, 134, 139, 48, 27, 166, 77, 146, 158, 231, 83,
            111, 229, 122, 60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244, 102, 143, 54, 65, 25, 63, 161, 1, 216, 80,
            73, 209, 76, 132, 187, 208, 89, 18, 169, 200, 196, 135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64,
            52, 217, 226, 250, 124, 123, 5, 202, 38, 147, 118, 126, 255, 82, 85, 212, 207, 206, 59, 227, 47, 16, 58, 17, 182, 189,
            28, 42, 223, 183, 170, 213, 119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9, 129, 22, 39, 253,
            19, 98, 108, 110, 79, 113, 224, 232, 178, 185, 112, 104, 218, 246, 97, 228, 251, 34, 242, 193, 238, 210, 144, 12, 191,
            179, 162, 241, 81, 51, 145, 235, 249, 14, 239, 107, 49, 192, 214, 31, 181, 199, 106, 157, 184, 84, 204, 176, 115, 121,
            50, 45, 127, 4, 150, 254, 138, 236, 205, 93, 222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180};
        for (int i = 0; i < 512; i++) PERMUTATION[i] = base[i & 255];
    }

    private MathLibrary() {}

    static void install(Host host) {
        Members math = new Members("MathHelpers")
                .function("remap", "(value: number, fromMin: number, fromMax: number, toMin: number, toMax: number) -> number", a -> {
                    double fromMin = a.number(1), fromMax = a.number(2), toMin = a.number(3), toMax = a.number(4);
                    if (fromMax == fromMin) return toMin;
                    return toMin + (a.number(0) - fromMin) * (toMax - toMin) / (fromMax - fromMin);
                })
                .function("lerp", "(a: number, b: number, t: number) -> number", a -> a.number(0) + (a.number(1) - a.number(0)) * a.number(2))
                .function("inverseLerp", "(a: number, b: number, value: number) -> number", a -> {
                    double from = a.number(0), to = a.number(1);
                    return from == to ? 0.0 : (a.number(2) - from) / (to - from);
                })
                .function("approach", "(value: number, target: number, step: number) -> number", a -> {
                    double value = a.number(0), target = a.number(1), step = a.number(2);
                    return value < target ? Math.min(value + step, target) : Math.max(value - step, target);
                })
                .function("smoothDamp", "(current: number, target: number, velocity: number?, smoothTime: number, dt: number, maxSpeed: number?) -> (number, number)",
                        MathLibrary::smoothDamp)
                .function("fbm", "(x: number, y: number?, z: number?, octaves: number?, lacunarity: number?, gain: number?) -> number", a -> {
                    int octaves = a.integer(3, 4);
                    double lacunarity = a.number(4, 2);
                    double gain = a.number(5, 0.5);
                    double sum = 0, amplitude = 1, frequency = 1, total = 0;
                    for (int n = 0; n < octaves; n++) {
                        sum += noise(a.number(0) * frequency, a.number(1, 0) * frequency, a.number(2, 0) * frequency) * amplitude;
                        total += amplitude;
                        amplitude *= gain;
                        frequency *= lacunarity;
                    }
                    return sum / total;
                })
                .function("bezier", "(points: { any }, t: number) -> any", a -> {
                    List<Object> work = new ArrayList<>(a.list(0));
                    if (work.isEmpty()) return null;
                    double t = a.number(1);
                    for (int n = work.size(); n >= 2; n--) {
                        for (int i = 0; i < n - 1; i++) {
                            work.set(i, add(host, work.get(i), scale(host, sub(host, work.get(i + 1), work.get(i)), t)));
                        }
                    }
                    return work.getFirst();
                })
                .function("catmullRom", "(points: { any }, t: number) -> any", a -> catmullRom(host, a.list(0), a.number(1)));
        host.extend("math", math);
        host.api().declare(math.decl());

        Members angle = new Members("Angle")
                .function("wrap", "(a: number) -> number", a -> wrap(a.number(0)))
                .function("delta", "(a: number, b: number) -> number", a -> wrap(a.number(1) - a.number(0)))
                .function("lerp", "(a: number, b: number, t: number) -> number",
                        a -> a.number(0) + wrap(a.number(1) - a.number(0)) * a.number(2));
        host.global("angle", "Angle", angle);
        host.declare(angle);

        Members random = new Members("Random")
                .function("range", "(min: number, max: number) -> number", a -> range(a.number(0), a.number(1)))
                .function("int", "(min: number, max: number) -> number",
                        a -> (double) ThreadLocalRandom.current().nextLong(a.integer(0), (long) a.integer(1) + 1))
                .function("chance", "(probability: number) -> boolean", a -> ThreadLocalRandom.current().nextDouble() < a.number(0))
                .function("pick", "(list: { any }) -> any", a -> {
                    List<Object> list = a.list(0);
                    return list.isEmpty() ? null : list.get(ThreadLocalRandom.current().nextInt(list.size()));
                })
                .function("shuffle", "(list: { any }) -> { any }", a -> {
                    List<Object> list = new ArrayList<>(a.list(0));
                    Collections.shuffle(list);
                    return list;
                })
                .function("unit", "() -> Vector3", a -> unit())
                .function("onSphere", "(radius: number) -> Vector3", a -> unit().mul(a.number(0)))
                .function("inSphere", "(radius: number) -> Vector3",
                        a -> unit().mul(a.number(0) * Math.cbrt(ThreadLocalRandom.current().nextDouble())))
                .function("inCircle", "(radius: number) -> Vector3", a -> {
                    double angleOf = range(0, 2 * Math.PI);
                    double r = a.number(0) * Math.sqrt(ThreadLocalRandom.current().nextDouble());
                    return new Vector3(Math.cos(angleOf) * r, 0, Math.sin(angleOf) * r);
                })
                .function("inBox", "(size: Vector3) -> Vector3", a -> {
                    Vector3 size = a.vector(0);
                    return new Vector3(range(-size.x() / 2, size.x() / 2), range(-size.y() / 2, size.y() / 2), range(-size.z() / 2, size.z() / 2));
                });
        host.global("random", "Random", random);
        host.declare(random);
    }

    private static Object smoothDamp(Args a) {
        double current = a.number(0), target = a.number(1), velocity = a.number(2, 0);
        double smoothTime = Math.max(0.0001, a.number(3));
        double dt = a.number(4);
        double omega = 2 / smoothTime;
        double x = omega * dt;
        double exp = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x);
        double change = current - target;
        if (a.has(5)) {
            double most = a.number(5) * smoothTime;
            change = Math.clamp(change, -most, most);
        }
        double goal = current - change;
        double temp = (velocity + omega * change) * dt;
        velocity = (velocity - omega * temp) * exp;
        double out = goal + (change + temp) * exp;
        if (target - current > 0 == out > target) {
            out = target;
            velocity = (out - target) / dt;
        }
        return Results.of(out, velocity);
    }

    private static Object catmullRom(Host host, List<Object> points, double t) {
        int count = points.size();
        if (count == 0) return null;
        if (count == 1) return points.getFirst();
        double scaled = Math.clamp(t, 0, 1) * (count - 1);
        int i = Math.min((int) Math.floor(scaled) + 1, count - 1);
        double u = scaled - (i - 1);
        Object p0 = points.get(Math.max(i - 1, 1) - 1);
        Object p1 = points.get(i - 1);
        Object p2 = points.get(i);
        Object p3 = points.get(Math.min(i + 2, count) - 1);
        double u2 = u * u, u3 = u2 * u;
        Object sum = scale(host, p1, 2);
        sum = add(host, sum, scale(host, sub(host, p2, p0), u));
        sum = add(host, sum, scale(host, sub(host, add(host, sub(host, scale(host, p0, 2), scale(host, p1, 5)), scale(host, p2, 4)), p3), u2));
        sum = add(host, sum, scale(host, add(host, sub(host, sub(host, scale(host, p1, 3), p0), scale(host, p2, 3)), p3), u3));
        return scale(host, sum, 0.5);
    }

    private static Object add(Host host, Object a, Object b) {
        return a instanceof Number x && b instanceof Number y ? x.doubleValue() + y.doubleValue() : host.operate(Host.Op.ADD, a, b);
    }

    private static Object sub(Host host, Object a, Object b) {
        return a instanceof Number x && b instanceof Number y ? x.doubleValue() - y.doubleValue() : host.operate(Host.Op.SUB, a, b);
    }

    private static Object scale(Host host, Object a, double by) {
        return a instanceof Number x ? x.doubleValue() * by : host.operate(Host.Op.MUL, a, by);
    }

    private static double wrap(double a) {
        double full = 2 * Math.PI;
        return ((a + Math.PI) % full + full) % full - Math.PI;
    }

    private static double range(double min, double max) {
        return min + ThreadLocalRandom.current().nextDouble() * (max - min);
    }

    private static Vector3 unit() {
        double z = range(-1, 1);
        double a = range(0, 2 * Math.PI);
        double r = Math.sqrt(1 - z * z);
        return new Vector3(r * Math.cos(a), z, r * Math.sin(a));
    }

    public static double noise(double x, double y, double z) {
        int xi = (int) Math.floor(x) & 255, yi = (int) Math.floor(y) & 255, zi = (int) Math.floor(z) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        double u = fade(x), v = fade(y), w = fade(z);
        int[] p = PERMUTATION;
        int a = p[xi] + yi, aa = p[a] + zi, ab = p[a + 1] + zi;
        int b = p[xi + 1] + yi, ba = p[b] + zi, bb = p[b + 1] + zi;
        return lerp(w, lerp(v, lerp(u, grad(p[aa], x, y, z), grad(p[ba], x - 1, y, z)),
                        lerp(u, grad(p[ab], x, y - 1, z), grad(p[bb], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(p[aa + 1], x, y, z - 1), grad(p[ba + 1], x - 1, y, z - 1)),
                        lerp(u, grad(p[ab + 1], x, y - 1, z - 1), grad(p[bb + 1], x - 1, y - 1, z - 1))));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
