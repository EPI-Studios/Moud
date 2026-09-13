package com.meekdev.moud.script.bind;

import com.meekdev.moud.core.instance.Character;
import com.meekdev.moud.core.instance.Humanoid;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Queries;
import com.meekdev.moud.core.instance.Rig;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vec3;
import com.meekdev.moud.core.nav.GridPath;
import com.meekdev.moud.core.nav.Walkers;
import com.meekdev.moud.script.api.BlockRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;

// game.path, and the bodies that walk them
public final class Paths {

    private static final Random RANDOM = new Random();
    private static final Vec3 CELL = new Vec3(0.98, 0.98, 0.98);

    private Paths() {}

    // solid where a block is, or where a part that collides fills the cell
    public static GridPath.Terrain terrain(LuaState state, Instance world) {
        BlockRef blocks = QueryMethods.blocksOf(state);
        Queries.Filter solid = new Queries.Filter(List.of(), false, true, null, null, null, Classes.PART);
        return (x, y, z) -> {
            if (blocks != null && blocks.solid(x, y, z)) return true;
            List<?> parts = Queries.inBox(world, CFrame.at(x + 0.5, y + 0.5, z + 0.5), CELL, part -> solid.test(part)
                    && !(part.parent() instanceof Character) && !(part.parent() != null && part.parent().parent() instanceof Character));
            return !parts.isEmpty();
        };
    }

    public static void install(LuaState state, Instance world) {
        state.getGlobal("game");
        state.newTable();
        function(state, "find", s -> {
            List<Vec3> path = GridPath.find(terrain(s, world), Values.vec3(s, 2), Values.vec3(s, 3), options(s, 4));
            if (path == null) {
                s.pushNil();
            } else {
                Plain.push(s, path);
            }
            return 1;
        });
        function(state, "isReachable", s -> {
            s.pushBoolean(GridPath.find(terrain(s, world), Values.vec3(s, 2), Values.vec3(s, 3), options(s, 4)) != null);
            return 1;
        });
        function(state, "randomPointNear", s -> {
            Vec3 point = GridPath.randomNear(terrain(s, world), Values.vec3(s, 2), s.checkNumber(3), options(s, 4), RANDOM);
            if (point == null) {
                s.pushNil();
            } else {
                Values.push(s, point);
            }
            return 1;
        });
        state.rawSetField(-2, "path");
        state.pop(1);

        Map<String, ToIntFunction<LuaState>> methods = new LinkedHashMap<>();
        // walks there along a path around what is in the way. false when there is no way
        methods.put("walkTo", s -> {
            Character body = BodyMethods.body(s);
            List<Vec3> path = GridPath.find(terrain(s, world), Transforms.world(body).position(), Values.vec3(s, 2), options(s, 3));
            if (path == null) {
                s.pushBoolean(false);
                return 1;
            }
            Walkers.walk(body, path);
            s.pushBoolean(true);
            return 1;
        });
        methods.put("follow", s -> {
            Character body = BodyMethods.body(s);
            if (!(s.toUserDataTagged(2, Proxies.TAG) instanceof Instance target)) throw s.error("follow wants something to follow");
            Walkers.follow(body, target, s.isNoneOrNil(3) ? 3 : s.checkNumber(3));
            return 0;
        });
        methods.put("stopWalking", s -> {
            Walkers.stop(BodyMethods.body(s));
            return 0;
        });
        methods.put("isWalking", s -> {
            Character body = BodyMethods.body(s);
            Humanoid living = Rig.humanoid(body);
            s.pushBoolean(Walkers.busy(body) || living != null && living.walking);
            return 1;
        });
        methods.put("jump", s -> {
            Walkers.jump(BodyMethods.body(s));
            return 0;
        });
        methods.put("lookAt", s -> {
            Character body = BodyMethods.body(s);
            Vec3 at = Transforms.world(body).position();
            face(body, Values.vec3(s, 2).sub(at));
            return 0;
        });
        methods.put("face", s -> {
            face(BodyMethods.body(s), Values.vec3(s, 2));
            return 0;
        });
        Proxies.classMethods(state, Classes.CHARACTER, methods, true);
    }

    // turns the body about up to look along a direction, height ignored
    private static void face(Character body, Vec3 direction) {
        Vec3 flat = new Vec3(direction.x(), 0, direction.z());
        if (flat.lengthSq() < 1e-9) return;
        double yaw = Math.atan2(-flat.x(), -flat.z());
        CFrame world = Transforms.world(body);
        Instances.setObj(body, Classes.CHARACTER.property("cframe"),
                Transforms.localFor(body, new CFrame(world.position(), Quat.euler(0, yaw, 0))));
    }

    private static GridPath.Options options(LuaState s, int at) {
        if (s.isNoneOrNil(at) || s.type(at) != LuaType.TABLE) return GridPath.Options.DEFAULT;
        return new GridPath.Options(number(s, at, "maxNodes", 20000), number(s, at, "maxDrop", 3), number(s, at, "height", 2));
    }

    private static int number(LuaState s, int at, String key, int fallback) {
        s.getField(at, key);
        int value = s.isNumber(-1) ? (int) s.toNumber(-1) : fallback;
        s.pop(1);
        return value;
    }

    private static void function(LuaState state, String name, ToIntFunction<LuaState> body) {
        state.pushFunction(LuaFunc.wrap(body::applyAsInt, "path:" + name));
        state.rawSetField(-2, name);
    }
}
