package com.meekdev.moud.script.bind.world;

import com.meekdev.moud.script.bind.LuaTables;
import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.NavMeshes;
import com.meekdev.moud.core.character.Humanoids;
import com.meekdev.moud.core.nav.Walkers;
import com.meekdev.moud.script.api.BlockRef;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import com.meekdev.moud.core.part.Part;
import java.util.function.Predicate;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Random;
import java.util.function.ToIntFunction;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaType;
import com.meekdev.moud.script.bind.player.BodyMethods;
import com.meekdev.moud.script.bind.Plain;
import com.meekdev.moud.script.bind.Proxies;
import com.meekdev.moud.script.bind.Values;

public final class Paths {

    private static final Random RANDOM = new Random();

    private Paths() {}

    private static final Map<Instance, NavMeshes> MESHES = new WeakHashMap<>();

    private static NavMeshes mesh(Instance world) {
        return MESHES.computeIfAbsent(world, w -> new NavMeshes());
    }

    private static Predicate<Part> ground() {
        Queries.Filter solid = new Queries.Filter(List.of(), false, true, null, null, null, Classes.PART);
        return part -> solid.test(part)
                && !(part.parent() instanceof Character) && !(part.parent() != null && part.parent().parent() instanceof Character);
    }

    private static NavMeshes.World source(LuaState state, Instance world) {
        BlockRef blocks = QueryMethods.blocksOf(state);
        Predicate<Part> ground = ground();
        return new NavMeshes.World() {
            @Override
            public boolean solid(int x, int y, int z) {
                return blocks != null && blocks.solid(x, y, z);
            }

            @Override
            public List<Box> boxes(Vector3 min, Vector3 max) {
                Vector3 size = max.sub(min);
                List<Box> boxes = new ArrayList<>();
                for (Part part : Queries.inBox(world, CFrame.at(min.add(size.mul(0.5))), size, ground)) {
                    boxes.add(new Box(Transforms.world(part), part.size));
                }
                return boxes;
            }
        };
    }

    public static Walkers.Finder finder(LuaState state, Instance world) {
        NavMeshes.World source = source(state, world);
        NavMeshes mesh = mesh(world);
        return (from, to, partial) -> mesh.find(source, from, to, partial);
    }

    public static void blockChanged(Instance world, int x, int y, int z) {
        NavMeshes mesh = MESHES.get(world);
        if (mesh != null) mesh.blockChanged(x, y, z);
    }

    private static Humanoids.Ground groundOf(LuaState state, Instance world) {
        Predicate<Part> ground = ground();
        Vector3 down = new Vector3(0, -1, 0);
        return (x, y, z) -> {
            Vector3 from = new Vector3(x, y + 1.1, z);
            double range = 1.1 + 4;
            Queries.Cast part = Queries.raycast(world, from, down, range, ground);
            BlockRef blocks = QueryMethods.blocksOf(state);
            BlockRef.Hit block = blocks == null ? null : blocks.raycast(from, down, part == null ? range : part.distance());
            if (block != null) return block.at().y();
            return part == null ? Double.NaN : part.at().y();
        };
    }

    public static void forget(Instance world) {
        MESHES.remove(world);
        if (world.tree() != null) Humanoids.ground(world.tree(), null);
    }

    public static void install(LuaState state, Instance world) {
        if (world.tree() != null) Humanoids.ground(world.tree(), groundOf(state, world));
        state.getGlobal("game");
        state.newTable();
        LuaTables.function(state, "path", "find", s -> {
            List<Vector3> path = mesh(world).find(source(s, world), Values.vec3(s, 2), Values.vec3(s, 3), partial(s, 4));
            if (path == null) {
                s.pushNil();
            } else {
                Plain.push(s, path);
            }
            return 1;
        });
        LuaTables.function(state, "path", "isReachable", s -> {
            s.pushBoolean(mesh(world).find(source(s, world), Values.vec3(s, 2), Values.vec3(s, 3), false) != null);
            return 1;
        });
        LuaTables.function(state, "path", "randomPointNear", s -> {
            Vector3 point = mesh(world).randomNear(source(s, world), Values.vec3(s, 2), s.checkNumber(3), RANDOM);
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
        methods.put("walkTo", s -> {
            Character body = BodyMethods.body(s);
            List<Vector3> path = mesh(world).find(source(s, world), Transforms.world(body).position(), Values.vec3(s, 2), partial(s, 3));
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
            if (!(s.toUserDataTagged(2, Proxies.TAG) instanceof Instance target)) throw s.error("follow expects something to follow");
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
            Vector3 at = Transforms.world(body).position();
            face(body, Values.vec3(s, 2).sub(at));
            return 0;
        });
        methods.put("face", s -> {
            face(BodyMethods.body(s), Values.vec3(s, 2));
            return 0;
        });
        Proxies.classMethods(state, Classes.CHARACTER, methods, true);
    }

    private static void face(Character body, Vector3 direction) {
        Vector3 flat = new Vector3(direction.x(), 0, direction.z());
        if (flat.lengthSq() < 1e-9) return;
        double yaw = Math.atan2(-flat.x(), -flat.z());
        CFrame world = Transforms.world(body);
        Instances.setObj(body, Classes.CHARACTER.property("cframe"),
                Transforms.localFor(body, new CFrame(world.position(), Quat.euler(0, yaw, 0))));
    }

    private static boolean partial(LuaState s, int at) {
        if (s.isNoneOrNil(at) || s.type(at) != LuaType.TABLE) return false;
        s.getField(at, "partial");
        boolean value = s.toBoolean(-1);
        s.pop(1);
        return value;
    }

}
