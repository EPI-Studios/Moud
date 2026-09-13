package com.meekdev.moud.script.host.debug;

import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.script.api.DebugRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.Members;
import java.util.Map;

public final class DebugLibrary {

    private static final Color DEFAULT = new Color(1, 0.8f, 0.2f, 1);

    private DebugLibrary() {}

    public static void install(Host host, Members game) {
        DebugRef debug = host.debug();
        if (debug == null) return;
        Members members = new Members("Debug")
                .method("drawLine", "(from: Vector3, to: Vector3, color: Color?, seconds: number?) -> ()", a -> {
                    debug.line(a.vector(1), a.vector(2), color(a, 3), a.number(4, 0));
                    return null;
                })
                .method("drawRay", "(from: Vector3, direction: Vector3, color: Color?, seconds: number?) -> ()", a -> {
                    Vector3 from = a.vector(1);
                    debug.line(from, from.add(a.vector(2)), color(a, 3), a.number(4, 0));
                    return null;
                })
                .method("drawBox", "(frame: CFrame, size: Vector3, color: Color?, seconds: number?) -> ()", a -> {
                    debug.box(a.cframe(1), a.vector(2), color(a, 3), a.number(4, 0));
                    return null;
                })
                .method("drawSphere", "(centre: Vector3, radius: number, color: Color?, seconds: number?) -> ()", a -> {
                    debug.sphere(a.vector(1), a.number(2), color(a, 3), a.number(4, 0));
                    return null;
                })
                .method("drawPoint", "(at: Vector3, color: Color?, seconds: number?) -> ()", a -> {
                    debug.sphere(a.vector(1), 0.1, color(a, 2), a.number(3, 0));
                    return null;
                })
                .method("label", "(at: Vector3, text: string, color: Color?, seconds: number?) -> ()", a -> {
                    debug.label(a.vector(1), a.string(2), color(a, 3), a.number(4, 0));
                    return null;
                })
                .method("watch", "(name: string, value: any) -> ()", a -> {
                    debug.watch(a.string(1), text(host, a.get(2)));
                    return null;
                })
                .method("clear", "() -> ()", a -> {
                    debug.clear();
                    return null;
                })
                .method("queryStats", "() -> { queries: number, partsTested: number }", a -> {
                    Queries.Stats stats = Queries.takeStats();
                    return Map.of("queries", (double) stats.queries(), "partsTested", (double) stats.partsTested());
                })
                .method("profile", "() -> { [string]: { milliseconds: number, calls: number, worst: number } }",
                        a -> host.profiler().take());
        host.declare(members);
        game.value("debug", "Debug", members);
    }

    private static String text(Host host, Object value) {
        if (value instanceof Number number) {
            double n = number.doubleValue();
            return n == Math.rint(n) && Math.abs(n) < 1e15 ? String.valueOf((long) n) : String.valueOf(n);
        }
        return host.text(value);
    }

    private static Color color(Args a, int at) {
        return a.get(at) instanceof Color c ? c : DEFAULT;
    }
}
