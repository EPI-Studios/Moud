package com.meekdev.moud.script.host.world;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.Humanoids;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Model;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.Agent;
import com.meekdev.moud.core.nav.BlockShape;
import com.meekdev.moud.core.nav.NavMeshes;
import com.meekdev.moud.core.nav.PathfindingLink;
import com.meekdev.moud.core.nav.PathfindingModifier;
import com.meekdev.moud.core.nav.Route;
import com.meekdev.moud.core.nav.Walkers;
import com.meekdev.moud.core.nav.Waypoint;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class Paths {

    private static final Random RANDOM = new Random();
    private static final Map<Instance, Map<Agent.Shape, NavMeshes>> MESHES = new WeakHashMap<>();
    private static final double RECHECK = 0.25;

    private Paths() {}

    private static NavMeshes mesh(Instance world, Agent agent) {
        return MESHES.computeIfAbsent(world, w -> new HashMap<>()).computeIfAbsent(agent.shape(), NavMeshes::new);
    }

    private static final class Path implements HostObject {

        private static final Members METHODS = new Members("Path")
                .declare("status", "PathStatus")
                .declare("blocked", "PathBlockedSignal")
                .declare("unblocked", "PathBlockedSignal")
                .method("compute", "(from: Vector3, to: Vector3) -> PathStatus", a -> a.self(Path.class).compute(a.vector(1), a.vector(2)))
                .method("waypoints", "() -> { PathWaypoint }", a -> a.self(Path.class).waypoints())
                .method("checkOcclusion", "(start: number?) -> number", a -> (double) a.self(Path.class).occluded(a.integer(1, 1)));

        final Agent agent;
        final Instance world;
        final NavMeshes.World source;
        final HostSignal blocked;
        final HostSignal unblocked;
        final Set<Character> walkers = Collections.newSetFromMap(new WeakHashMap<>());
        Route route;
        boolean computed;
        int blockedAt = -1;

        Path(Host host, Agent agent, NavMeshes.World source) {
            this.agent = agent;
            this.world = host.world();
            this.source = source;
            blocked = new HostSignal(host, "PathBlockedSignal", "path.blocked");
            unblocked = new HostSignal(host, "PathBlockedSignal", "path.unblocked");
        }

        String compute(Vector3 from, Vector3 to) {
            route = mesh(world, agent).route(source, from, to, agent);
            computed = true;
            blockedAt = -1;
            for (Character body : new ArrayList<>(walkers)) {
                if (!Walkers.walking(body, this)) {
                    walkers.remove(body);
                } else if (route.found()) {
                    Walkers.walkAlong(body, new ArrayList<>(route.ahead()), agent, this);
                } else {
                    Walkers.stop(body);
                }
            }
            return status();
        }

        String status() {
            if (!computed) return "notComputed";
            return switch (route.status()) {
                case SUCCESS -> "success";
                case CLOSEST -> "closest";
                case NO_PATH -> "noPath";
                case TOO_FAR -> "tooFar";
            };
        }

        List<Object> waypoints() {
            List<Object> out = new ArrayList<>();
            if (route == null) return out;
            for (Waypoint waypoint : route.waypoints()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("position", waypoint.position());
                entry.put("action", waypoint.action().name().toLowerCase());
                if (waypoint.label() != null) entry.put("label", waypoint.label());
                out.add(entry);
            }
            return out;
        }

        int occluded(int start) {
            if (route == null || route.waypoints().size() < 2) return -1;
            int first = Math.clamp(start, 1, route.waypoints().size()) - 1;
            List<Vector3> ahead = new ArrayList<>();
            for (int i = first + 1; i < route.waypoints().size(); i++) ahead.add(route.waypoints().get(i).position());
            if (ahead.isEmpty()) return -1;
            int hit = mesh(world, agent).blocked(source, route.waypoints().get(first).position(), ahead, agent);
            return hit < 0 ? -1 : first + hit + 2;
        }

        void watch() {
            if (route == null || !route.found() || blocked.count() + unblocked.count() == 0) return;
            int now = occluded(1);
            if (now == blockedAt) return;
            int was = blockedAt;
            blockedAt = now;
            if (was > 0) unblocked.fire((double) was);
            if (now > 0) blocked.fire((double) now);
        }

        @Override
        public String typeName() {
            return "Path";
        }

        @Override
        public Object get(String key) {
            return switch (key) {
                case "status" -> status();
                case "blocked" -> blocked;
                case "unblocked" -> unblocked;
                default -> METHODS.get(key);
            };
        }
    }

    private static Predicate<Part> ground() {
        Queries.Filter solid = new Queries.Filter(List.of(), false, true, null, null, null, Classes.PART);
        return part -> solid.test(part) && !insideBody(part);
    }

    private static boolean insideBody(Part part) {
        return part.parent() instanceof Character || part.parent() != null && part.parent().parent() instanceof Character;
    }

    private static String label(PathfindingModifier modifier) {
        return modifier.label.isBlank() ? null : modifier.label;
    }

    private static PathfindingModifier modifierOf(Part part) {
        for (Instance child : part.children()) {
            if (child instanceof PathfindingModifier modifier) return modifier;
        }
        for (Instance at = part.parent(); at instanceof Model; at = at.parent()) {
            for (Instance child : at.children()) {
                if (child instanceof PathfindingModifier modifier) return modifier;
            }
        }
        return null;
    }

    private static void partsOf(Instance at, List<Part> out) {
        if (at instanceof Part part) out.add(part);
        if (at instanceof Model) {
            for (Instance child : at.children()) partsOf(child, out);
        }
    }

    private static boolean overlaps(Part part, Vector3 min, Vector3 max) {
        Vector3 at = Transforms.world(part).position();
        double reach = part.size.length() * 0.5;
        return at.x() + reach >= min.x() && at.x() - reach <= max.x() && at.y() + reach >= min.y() && at.y() - reach <= max.y()
                && at.z() + reach >= min.z() && at.z() - reach <= max.z();
    }

    private static NavMeshes.World source(Host host) {
        Instance world = host.world();
        BlockRef blocks = host.blocks();
        Predicate<Part> ground = ground();
        return new NavMeshes.World() {
            @Override
            public BlockShape block(int x, int y, int z) {
                return blocks == null ? BlockShape.AIR : blocks.shape(x, y, z);
            }

            @Override
            public List<Box> boxes(Vector3 min, Vector3 max) {
                Vector3 size = max.sub(min);
                List<Box> boxes = new ArrayList<>();
                for (Part part : Queries.inBox(world, CFrame.at(min.add(size.mul(0.5))), size, ground)) {
                    PathfindingModifier modifier = modifierOf(part);
                    boxes.add(modifier == null ? new Box(Transforms.world(part), part.size)
                            : new Box(Transforms.world(part), part.size, !modifier.passThrough, label(modifier)));
                }
                InstanceTree tree = world.tree();
                if (tree == null) return boxes;
                Map<Part, Boolean> seen = new IdentityHashMap<>();
                for (PathfindingModifier modifier : tree.ofClass(Classes.PATHFINDING_MODIFIER)) {
                    if (label(modifier) == null || modifier.parent() == null) continue;
                    List<Part> parts = new ArrayList<>();
                    partsOf(modifier.parent(), parts);
                    for (Part part : parts) {
                        if (part.collides && !insideBody(part) || seen.put(part, true) != null || !overlaps(part, min, max)) continue;
                        if (modifierOf(part) != modifier) continue;
                        boxes.add(new Box(Transforms.world(part), part.size, false, label(modifier)));
                    }
                }
                return boxes;
            }

            @Override
            public List<Link> links(Vector3 min, Vector3 max) {
                InstanceTree tree = world.tree();
                if (tree == null) return List.of();
                List<Link> links = new ArrayList<>();
                for (PathfindingLink link : tree.ofClass(Classes.PATHFINDING_LINK)) {
                    if (!(link.attachment0 instanceof Spatial a) || !(link.attachment1 instanceof Spatial b)) continue;
                    if (!a.isAlive() || !b.isAlive()) continue;
                    Vector3 from = Transforms.world(a).position(), to = Transforms.world(b).position();
                    boolean near = inside(from, min, max) || link.bidirectional && inside(to, min, max);
                    if (!near) continue;
                    String label = link.label.isBlank() ? null : link.label;
                    links.add(new Link(from, to, link.bidirectional, label));
                }
                return links;
            }
        };
    }

    private static boolean inside(Vector3 at, Vector3 min, Vector3 max) {
        return at.x() >= min.x() && at.x() <= max.x() && at.z() >= min.z() && at.z() <= max.z();
    }

    public static void blockChanged(Instance world, int x, int y, int z) {
        Map<Agent.Shape, NavMeshes> meshes = MESHES.get(world);
        if (meshes == null) return;
        for (NavMeshes mesh : meshes.values()) mesh.blockChanged(x, y, z);
    }

    public static void install(Host host, Members game) {
        Instance world = host.world();
        if (world.tree() != null) Humanoids.ground(world.tree(), groundOf(host));
        List<WeakReference<Path>> paths = new ArrayList<>();
        host.onClose(() -> {
            MESHES.remove(world);
            paths.clear();
            if (world.tree() != null) Humanoids.ground(world.tree(), null);
        });
        NavMeshes.World source = source(host);
        Walkers.Finder finder = new Walkers.Finder() {
            @Override
            public Route find(Vector3 from, Vector3 to, Agent agent) {
                return mesh(world, agent).route(source, from, to, agent);
            }

            @Override
            public int blocked(Vector3 from, List<Vector3> ahead, Agent agent) {
                return mesh(world, agent).blocked(source, from, ahead, agent);
            }
        };
        double[] clock = {0, 0};
        host.onStep(dt -> {
            clock[0] += dt;
            Walkers.step(world.tree(), finder, clock[0]);
            if (clock[0] - clock[1] < RECHECK) return;
            clock[1] = clock[0];
            paths.removeIf(held -> held.get() == null);
            for (WeakReference<Path> held : new ArrayList<>(paths)) {
                Path path = held.get();
                if (path != null) path.watch();
            }
        });

        host.api().alias("PathOptions", "{ partial: boolean?, radius: number?, height: number?, canJump: boolean?, "
                + "canClimb: boolean?, spacing: number?, costs: { [string]: number }? }");
        host.api().alias("PathAction", "\"walk\" | \"jump\" | \"climb\" | \"custom\"");
        host.api().alias("PathStatus", "\"success\" | \"closest\" | \"noPath\" | \"tooFar\" | \"notComputed\"");
        host.api().alias("PathWaypoint", "{ position: Vector3, action: PathAction, label: string? }");
        host.api().declare(HostSignal.decl("PathBlockedSignal", "(index: number) -> ()"));
        host.api().declare(Path.METHODS.decl());
        Members service = new Members("Pathfinding")
                .method("find", "(from: Vector3, to: Vector3, options: PathOptions?) -> { Vector3 }?", a -> {
                    Agent agent = agent(a.get(3));
                    Route route = mesh(world, agent).route(source, a.vector(1), a.vector(2), agent);
                    return usable(route, partial(a, 3)) ? new ArrayList<Object>(route.positions()) : null;
                })
                .method("isReachable", "(from: Vector3, to: Vector3, options: PathOptions?) -> boolean", a -> {
                    Agent agent = agent(a.get(3));
                    return mesh(world, agent).route(source, a.vector(1), a.vector(2), agent).status() == Route.Status.SUCCESS;
                })
                .method("randomPointNear", "(point: Vector3, radius: number, options: PathOptions?) -> Vector3?",
                        a -> mesh(world, agent(a.get(3))).randomNear(source, a.vector(1), a.number(2), RANDOM))
                .method("create", "(options: PathOptions?) -> Path", a -> {
                    Path path = new Path(host, agent(a.get(1)), source);
                    paths.add(new WeakReference<>(path));
                    return path;
                });
        host.declare(service);
        game.value("path", "Pathfinding", service);

        Members bodies = host.instances().of(Classes.CHARACTER);
        bodies.method("walkTo", "(point: Vector3, options: PathOptions?) -> boolean", a -> {
            Character body = a.self(Character.class);
            Agent agent = agent(a.get(2));
            boolean partial = partial(a, 2);
            Route route = mesh(world, agent).route(source, Transforms.world(body).position(), a.vector(1), agent);
            if (!usable(route, partial)) return false;
            Walkers.walk(body, new ArrayList<>(route.ahead()), a.vector(1), agent, partial);
            return true;
        });
        bodies.method("walkAlong", "(path: Path) -> boolean", a -> {
            Character body = a.self(Character.class);
            if (!(a.get(1) instanceof Path path)) throw a.error("walkAlong expects a path from game.path:create");
            if (path.route == null || !path.route.found()) return false;
            Walkers.walkAlong(body, new ArrayList<>(path.route.ahead()), path.agent, path);
            path.walkers.add(body);
            return true;
        });
        bodies.method("follow", "(target: Instance, distance: number?, options: PathOptions?) -> ()", a -> {
            Walkers.follow(a.self(Character.class), a.instance(1), a.number(2, 3), agent(a.get(3)));
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

    private static boolean usable(Route route, boolean partial) {
        return route.status() == Route.Status.SUCCESS || partial && route.status() == Route.Status.CLOSEST;
    }

    private static Agent agent(Object options) {
        if (!(options instanceof Map<?, ?> table)) return Agent.PLAYER;
        Agent base = Agent.PLAYER;
        Map<String, Double> costs = new HashMap<>();
        if (table.get("costs") instanceof Map<?, ?> given) {
            for (Map.Entry<?, ?> entry : given.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof Number value) costs.put(key, value.doubleValue());
            }
        }
        return new Agent(number(table, "radius", base.radius()), number(table, "height", base.height()),
                flag(table, "canJump", base.canJump()), flag(table, "canClimb", base.canClimb()),
                number(table, "spacing", base.spacing()), costs);
    }

    private static double number(Map<?, ?> table, String key, double fallback) {
        return table.get(key) instanceof Number n ? n.doubleValue() : fallback;
    }

    private static boolean flag(Map<?, ?> table, String key, boolean fallback) {
        return table.get(key) instanceof Boolean b ? b : fallback;
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
