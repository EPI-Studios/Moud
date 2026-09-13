package com.meekdev.moud.script.host.world;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.Humanoids;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.NavMeshes;
import com.meekdev.moud.core.nav.Walkers;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.Members;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class Paths {

    private static final Random RANDOM = new Random();
    private static final Map<Instance, NavMeshes> MESHES = new WeakHashMap<>();

    private Paths() {}

    private static NavMeshes mesh(Instance world) {
        return MESHES.computeIfAbsent(world, w -> new NavMeshes());
    }

    private static Predicate<Part> ground() {
        Queries.Filter solid = new Queries.Filter(List.of(), false, true, null, null, null, Classes.PART);
        return part -> solid.test(part)
                && !(part.parent() instanceof Character) && !(part.parent() != null && part.parent().parent() instanceof Character);
    }

    private static NavMeshes.World source(Host host) {
        Instance world = host.world();
        BlockRef blocks = host.blocks();
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

    public static void blockChanged(Instance world, int x, int y, int z) {
        NavMeshes mesh = MESHES.get(world);
        if (mesh != null) mesh.blockChanged(x, y, z);
    }

    public static void install(Host host, Members game) {
        Instance world = host.world();
        if (world.tree() != null) Humanoids.ground(world.tree(), groundOf(host));
        host.onClose(() -> {
            MESHES.remove(world);
            if (world.tree() != null) Humanoids.ground(world.tree(), null);
        });
        NavMeshes.World source = source(host);
        Walkers.Finder finder = (from, to, partial) -> mesh(world).find(source, from, to, partial);
        host.onStep(dt -> Walkers.step(finder, System.nanoTime() / 1e9));

        host.api().alias("PathOptions", "{ partial: boolean? }");
        Members path = new Members("Path")
                .method("find", "(from: Vector3, to: Vector3, options: PathOptions?) -> { Vector3 }?", a -> {
                    List<Vector3> found = mesh(world).find(source, a.vector(1), a.vector(2), partial(a, 3));
                    return found == null ? null : new ArrayList<Object>(found);
                })
                .method("isReachable", "(from: Vector3, to: Vector3, options: PathOptions?) -> boolean",
                        a -> mesh(world).find(source, a.vector(1), a.vector(2), false) != null)
                .method("randomPointNear", "(point: Vector3, radius: number, options: PathOptions?) -> Vector3?",
                        a -> mesh(world).randomNear(source, a.vector(1), a.number(2), RANDOM));
        host.declare(path);
        game.value("path", "Path", path);

        Members bodies = host.instances().of(Classes.CHARACTER);
        bodies.method("walkTo", "(point: Vector3, options: PathOptions?) -> boolean", a -> {
            Character body = a.self(Character.class);
            List<Vector3> found = mesh(world).find(source, Transforms.world(body).position(), a.vector(1), partial(a, 2));
            if (found == null) return false;
            Walkers.walk(body, found);
            return true;
        });
        bodies.method("follow", "(target: Instance, distance: number?) -> ()", a -> {
            Walkers.follow(a.self(Character.class), a.instance(1), a.number(2, 3));
            return null;
        });
        bodies.method("stopWalking", "() -> ()", a -> {
            Walkers.stop(a.self(Character.class));
            return null;
        });
        bodies.method("isWalking", "() -> boolean", a -> {
            Character body = a.self(Character.class);
            Humanoid living = Rig.humanoid(body);
            return Walkers.busy(body) || living != null && living.walking;
        });
        bodies.method("jump", "() -> ()", a -> {
            Walkers.jump(a.self(Character.class));
            return null;
        });
        bodies.method("lookAt", "(point: Vector3) -> ()", a -> {
            Character body = a.self(Character.class);
            face(body, a.vector(1).sub(Transforms.world(body).position()));
            return null;
        });
        bodies.method("face", "(direction: Vector3) -> ()", a -> {
            face(a.self(Character.class), a.vector(1));
            return null;
        });
    }

    private static Humanoids.Ground groundOf(Host host) {
        Predicate<Part> ground = ground();
        Vector3 down = new Vector3(0, -1, 0);
        return (x, y, z) -> {
            Vector3 from = new Vector3(x, y + 1.1, z);
            double range = 1.1 + 4;
            Queries.Cast part = Queries.raycast(host.world(), from, down, range, ground);
            BlockRef blocks = host.blocks();
            BlockRef.Hit block = blocks == null ? null : blocks.raycast(from, down, part == null ? range : part.distance());
            if (block != null) return block.at().y();
            return part == null ? Double.NaN : part.at().y();
        };
    }

    private static void face(Character body, Vector3 direction) {
        Vector3 flat = new Vector3(direction.x(), 0, direction.z());
        if (flat.lengthSq() < 1e-9) return;
        double yaw = Math.atan2(-flat.x(), -flat.z());
        CFrame world = Transforms.world(body);
        Instances.setObj(body, Classes.CHARACTER.property("cframe"),
                Transforms.localFor(body, new CFrame(world.position(), Quat.euler(0, yaw, 0))));
    }

    private static boolean partial(Args a, int at) {
        return a.get(at) instanceof Map<?, ?> options && Boolean.TRUE.equals(options.get("partial"));
    }
}
