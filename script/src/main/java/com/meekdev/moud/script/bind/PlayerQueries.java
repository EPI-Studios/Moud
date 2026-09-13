package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;

// game.players:all, near, nearest and bodyOf
//
// read off the tree's index of characters rather than by walking the world, so the cost is the number of
// bodies and not the number of things in the place. only bodies a player is wearing count
public final class PlayerQueries {

    private record Found(Character body, double distanceSq) {}

    private PlayerQueries() {}

    // expects the players table on top of the stack
    public static void install(LuaState state, Instance world) {
        InstanceTree tree = world.tree();
        method(state, "all", s -> {
            push(s, bodies(tree, null, Double.POSITIVE_INFINITY, null));
            return 1;
        });
        // every body within radius of a point, nearest first
        method(state, "near", s -> {
            Vec3 at = Values.vec3(s, 2);
            double radius = s.checkNumber(3);
            Instance except = s.isNoneOrNil(4) ? null : (Instance) s.toUserDataTagged(4, Proxies.TAG);
            push(s, bodies(tree, at, radius, except));
            return 1;
        });
        // the one nearest body, optionally within radius, and how far it is
        method(state, "nearest", s -> {
            Vec3 at = Values.vec3(s, 2);
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
        method(state, "count", s -> {
            int n = 0;
            for (Character body : tree.ofClass(Classes.CHARACTER)) if (body.worn()) n++;
            s.pushNumber(n);
            return 1;
        });
    }

    private static List<Found> bodies(InstanceTree tree, Vec3 at, double radius, Instance except) {
        double limit = radius * radius;
        List<Found> out = new ArrayList<>();
        for (Character body : tree.ofClass(Classes.CHARACTER)) {
            if (!body.worn() || body == except || !body.isAlive()) continue;
            double d = 0;
            if (at != null) {
                Vec3 p = Transforms.world(body).position();
                double dx = p.x() - at.x(), dy = p.y() - at.y(), dz = p.z() - at.z();
                d = dx * dx + dy * dy + dz * dz;
                if (d > limit) continue;
            }
            out.add(new Found(body, d));
        }
        if (at != null) out.sort((a, b) -> Double.compare(a.distanceSq(), b.distanceSq()));
        return out;
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
