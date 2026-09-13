package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.instance.Part;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;

public final class PlayerQueries {

    private record Found(Character body, double distanceSq) {}

    private PlayerQueries() {}

    public static void install(LuaState state, Instance world) {
        InstanceTree tree = world.tree();
        method(state, "all", s -> {
            push(s, bodies(tree, null, Double.POSITIVE_INFINITY, null));
            return 1;
        });
        method(state, "near", s -> {
            Vector3 at = Values.vec3(s, 2);
            double radius = s.checkNumber(3);
            Instance except = s.isNoneOrNil(4) ? null : (Instance) s.toUserDataTagged(4, Proxies.TAG);
            push(s, bodies(tree, at, radius, except));
            return 1;
        });
        method(state, "nearest", s -> {
            Vector3 at = Values.vec3(s, 2);
            double radius = s.isNoneOrNil(3) ? Double.POSITIVE_INFINITY : s.checkNumber(3);
            Instance except = s.isNoneOrNil(4) ? null : (Instance) s.toUserDataTagged(4, Proxies.TAG);
            List<Found> found = bodies(tree, at, radius, except);
            if (found.isEmpty()) {
                s.pushNil();
                return 1;
            }
            Proxies.push(s, found.getFirst().body());
            s.pushNumber(Math.sqrt(found.getFirst().distanceSq()));
            return 2;
        });
        method(state, "bodyOf", s -> {
            String player = s.checkString(2);
            for (Character body : tree.ofClass(Classes.CHARACTER)) {
                if (body.owner.equals(player)) {
                    Proxies.push(s, body);
                    return 1;
                }
            }
            s.pushNil();
            return 1;
        });
        method(state, "inBox", s -> {
            CFrame frame = Values.cframe(s, 2);
            Vector3 size = Values.vec3(s, 3);
            Instance except = optional(s, 4);
            push(s, filtered(tree, except, body -> inside(frame, size, position(body))));
            return 1;
        });
        method(state, "inPart", s -> {
            if (!(s.toUserDataTagged(2, Proxies.TAG) instanceof Part part)) throw s.error("inPart wants a part");
            Instance except = optional(s, 3);
            CFrame frame = Transforms.world(part);
            push(s, filtered(tree, except, body -> inside(frame, part.size, position(body))));
            return 1;
        });
        method(state, "inCone", s -> {
            Vector3 at = Values.vec3(s, 2);
            Vector3 way = Values.vec3(s, 3).normalize();
            double cos = Math.cos(Math.toRadians(s.checkNumber(4)));
            double range = s.checkNumber(5);
            Instance except = optional(s, 6);
            List<Found> near = bodies(tree, at, range, except);
            List<Found> out = new ArrayList<>();
            for (Found found : near) {
                Vector3 to = position(found.body()).sub(at);
                double length = to.length();
                if (length < 1e-6 || to.dot(way) / length >= cos) out.add(found);
            }
            push(s, out);
            return 1;
        });
        method(state, "visibleFrom", s -> {
            Vector3 at = Values.vec3(s, 2);
            double range = s.checkNumber(3);
            Instance except = optional(s, 4);
            List<Found> out = new ArrayList<>();
            for (Found found : bodies(tree, at, range, except)) {
                List<Instance> ignore = except == null ? List.of(found.body()) : List.of(found.body(), except);
                if (QueryMethods.clear(s, world, at, eye(found.body()), ignore)) out.add(found);
            }
            push(s, out);
            return 1;
        });
        method(state, "withTag", s -> {
            String tag = s.checkString(2);
            push(s, filtered(tree, null, body -> body.hasTag(tag)));
            return 1;
        });
        method(state, "random", s -> {
            List<Found> all = bodies(tree, null, Double.POSITIVE_INFINITY, optional(s, 2));
            if (all.isEmpty()) {
                s.pushNil();
            } else {
                Proxies.push(s, all.get(ThreadLocalRandom.current().nextInt(all.size())).body());
            }
            return 1;
        });
        method(state, "sortedByDistance", s -> {
            push(s, bodies(tree, Values.vec3(s, 2), Double.POSITIVE_INFINITY, null));
            return 1;
        });
        method(state, "inRange", s -> {
            Instance a = (Instance) s.toUserDataTagged(2, Proxies.TAG);
            Instance b = (Instance) s.toUserDataTagged(3, Proxies.TAG);
            if (a == null || b == null) throw s.error("inRange wants two instances and a range");
            double range = s.checkNumber(4);
            s.pushBoolean(position(a).sub(position(b)).lengthSq() <= range * range);
            return 1;
        });
        method(state, "fromName", s -> {
            String name = s.checkString(2);
            for (Character body : tree.ofClass(Classes.CHARACTER)) {
                if (body.worn() && body.name().equalsIgnoreCase(name)) {
                    Proxies.push(s, body);
                    return 1;
                }
            }
            s.pushNil();
            return 1;
        });
        method(state, "count", s -> {
            int n = 0;
            for (Character body : tree.ofClass(Classes.CHARACTER)) if (body.worn()) n++;
            s.pushNumber(n);
            return 1;
        });
    }

    private static List<Found> bodies(InstanceTree tree, Vector3 at, double radius, Instance except) {
        double limit = radius * radius;
        List<Found> out = new ArrayList<>();
        for (Character body : tree.ofClass(Classes.CHARACTER)) {
            if (!body.worn() || body == except || !body.isAlive()) continue;
            double d = 0;
            if (at != null) {
                Vector3 p = Transforms.world(body).position();
                double dx = p.x() - at.x(), dy = p.y() - at.y(), dz = p.z() - at.z();
                d = dx * dx + dy * dy + dz * dz;
                if (d > limit) continue;
            }
            out.add(new Found(body, d));
        }
        if (at != null) out.sort((a, b) -> Double.compare(a.distanceSq(), b.distanceSq()));
        return out;
    }

    private static List<Found> filtered(InstanceTree tree, Instance except, Predicate<Character> keep) {
        List<Found> out = new ArrayList<>();
        for (Character body : tree.ofClass(Classes.CHARACTER)) {
            if (body.worn() && body != except && body.isAlive() && keep.test(body)) out.add(new Found(body, 0));
        }
        return out;
    }

    private static Instance optional(LuaState s, int at) {
        return s.isNoneOrNil(at) ? null : (Instance) s.toUserDataTagged(at, Proxies.TAG);
    }

    static Vector3 position(Instance instance) {
        return Transforms.world(instance).position();
    }

    static Vector3 eye(Instance instance) {
        Vector3 at = position(instance);
        return instance instanceof Character body ? at.add(new Vector3(0, body.height * body.scale * 0.9, 0)) : at;
    }

    static boolean inside(CFrame frame, Vector3 size, Vector3 point) {
        Vector3 local = frame.inverse().mul(CFrame.at(point)).position();
        return Math.abs(local.x()) <= size.x() / 2 && Math.abs(local.y()) <= size.y() / 2
                && Math.abs(local.z()) <= size.z() / 2;
    }

    private static void push(LuaState s, List<Found> found) {
        s.createTable(found.size(), 0);
        for (int n = 0; n < found.size(); n++) {
            Proxies.push(s, found.get(n).body());
            s.rawSetI(-2, n + 1);
        }
    }

    private static void method(LuaState state, String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(body::applyAsInt, "players:" + name));
        state.rawSetField(-2, name);
    }
}
