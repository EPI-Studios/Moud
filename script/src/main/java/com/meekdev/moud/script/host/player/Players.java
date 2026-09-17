package com.meekdev.moud.script.host.player;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.character.Humanoid;
import com.meekdev.moud.core.character.HumanoidState;
import com.meekdev.moud.core.character.Rig;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.script.api.CameraRef;
import com.meekdev.moud.script.api.ControlsRef;
import com.meekdev.moud.script.api.InputRef;
import com.meekdev.moud.script.api.PlayerRef;
import com.meekdev.moud.script.api.RosterRef;
import com.meekdev.moud.script.api.SpawnRef;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostObject;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Results;
import com.meekdev.moud.script.host.world.WorldQueries;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class Players {

    private record Found(Character body, double distanceSq) {}

    private record Player(PlayerRef ref) implements HostObject {

        private static final Members METHODS = new Members("Player")
                .declare("name", "string")
                .declare("id", "string")
                .declare("character", "Instance?")
                .declare("controls", "PlayerControls")
                .method("spawn", "(position: Vector3?) -> ()", a -> {
                    a.self(Player.class).ref().spawn(a.has(1) ? a.vector(1) : null);
                    return null;
                })
                .method("kick", "(message: string?) -> ()", a -> {
                    a.self(Player.class).ref().kick(a.has(1) ? a.string(1) : "");
                    return null;
                })
                .method("ping", "() -> number", a -> a.self(Player.class).ref().ping())
                .method("viewTime", "() -> number", a -> a.self(Player.class).ref().viewTime());

        @Override
        public String typeName() {
            return "Player";
        }

        @Override
        public Object get(String key) {
            return switch (key) {
                case "name" -> ref.name();
                case "id" -> ref.id();
                case "character" -> ref.character();
                case "controls" -> controls(ref.controls());
                default -> METHODS.get(key);
            };
        }
    }

    private record Me(Host host) implements PlayerRef {

        @Override
        public String name() {
            Instance body = character();
            return body == null ? host.me() : body.name();
        }

        @Override
        public String id() {
            return host.me();
        }

        @Override
        public Instance character() {
            Instance body = host.own().get();
            return body == null || !body.isAlive() ? null : body;
        }

        @Override
        public void spawn(Vector3 position) {
            throw new HostError("player:spawn runs on the server");
        }

        @Override
        public ControlsRef controls() {
            if (host.controls() == null) throw new HostError("player.controls is not available here");
            return host.controls();
        }

        @Override
        public void kick(String message) {
            throw new HostError("player:kick runs on the server");
        }

        @Override
        public double ping() {
            return 0;
        }

        @Override
        public double viewTime() {
            return 0;
        }
    }

    private Players() {}

    public static HostObject wrap(Host host, PlayerRef player) {
        return new Player(player);
    }

    public static HostObject local(Host host) {
        return new Player(new Me(host));
    }

    public static String idOf(Object value) {
        return switch (value) {
            case Player player -> player.ref().id();
            case Character body when body.hasPlayer() -> body.owner;
            case null, default -> null;
        };
    }

    static Members controls(ControlsRef ref) {
        Members controls = new Members("PlayerControls");
        for (String name : ControlsRef.NAMES) {
            controls.field(name, "boolean", () -> ref.enabled(name), value -> {
                if (!(value instanceof Boolean on)) throw new HostError("controls.%s expects true or false", name);
                ref.enabled(name, on);
            });
        }
        return controls
                .method("enable", "() -> ()", a -> {
                    for (String name : ControlsRef.NAMES) ref.enabled(name, true);
                    return null;
                })
                .method("disable", "() -> ()", a -> {
                    for (String name : ControlsRef.NAMES) ref.enabled(name, false);
                    return null;
                });
    }

    public static void install(Host host, Members game) {
        HumanoidLibrary.install(host);
        AnimationLibrary.install(host);
        host.api().declare(Player.METHODS.decl());
        host.api().declare(controls(new ControlsRef() {
            @Override
            public boolean enabled(String control) {
                return true;
            }

            @Override
            public void enabled(String control, boolean on) {}
        }).decl());
        InstanceTree tree = host.world().tree();
        Members players = new Members("Players")
                .value("joined", "PlayerSignal", host.joinedSignal())
                .value("leaving", "PlayerSignal", host.leavingSignal())
                .value("spawned", "PlayerSignal", host.spawnedSignal())
                .method("all", "() -> { Instance }", a -> bodies(bodies(tree, null, Double.POSITIVE_INFINITY, null)))
                .method("near", "(position: Vector3, radius: number, except: Instance?) -> { Instance }",
                        a -> bodies(bodies(tree, a.vector(1), a.number(2), a.instance(3, null))))
                .method("nearest", "(position: Vector3, radius: number?, except: Instance?) -> (Instance?, number?)", a -> {
                    List<Found> found = bodies(tree, a.vector(1), a.number(2, Double.POSITIVE_INFINITY), a.instance(3, null));
                    return found.isEmpty() ? null : Results.of(found.getFirst().body(), Math.sqrt(found.getFirst().distanceSq()));
                })
                .method("bodyOf", "(player: string) -> Instance?", a -> {
                    String player = a.string(1);
                    for (Character body : tree.ofClass(Classes.CHARACTER)) {
                        if (body.owner.equals(player)) return body;
                    }
                    return null;
                })
                .method("inBox", "(frame: CFrame, size: Vector3, except: Instance?) -> { Instance }", a -> {
                    CFrame frame = a.cframe(1);
                    Vector3 size = a.vector(2);
                    return bodies(filtered(tree, a.instance(3, null), body -> WorldQueries.inside(frame, size, position(body))));
                })
                .method("inPart", "(part: Instance, except: Instance?) -> { Instance }", a -> {
                    if (!(a.get(1) instanceof Part part)) throw a.error("expects a part");
                    CFrame frame = Transforms.world(part);
                    return bodies(filtered(tree, a.instance(2, null), body -> WorldQueries.inside(frame, part.size, position(body))));
                })
                .method("inCone", "(position: Vector3, direction: Vector3, angle: number, range: number, except: Instance?) -> { Instance }", a -> {
                    Vector3 at = a.vector(1);
                    Vector3 way = a.vector(2).normalize();
                    double cos = Math.cos(Math.toRadians(a.number(3)));
                    List<Found> out = new ArrayList<>();
                    for (Found found : bodies(tree, at, a.number(4), a.instance(5, null))) {
                        Vector3 to = position(found.body()).sub(at);
                        double length = to.length();
                        if (length < 1e-6 || to.dot(way) / length >= cos) out.add(found);
                    }
                    return bodies(out);
                })
                .method("visibleFrom", "(position: Vector3, range: number, except: Instance?) -> { Instance }", a -> {
                    Vector3 at = a.vector(1);
                    Instance except = a.instance(3, null);
                    List<Found> out = new ArrayList<>();
                    for (Found found : bodies(tree, at, a.number(2), except)) {
                        List<Instance> ignore = except == null ? List.of(found.body()) : List.of(found.body(), except);
                        if (WorldQueries.clear(host, host.world(), at, eye(found.body()), ignore)) out.add(found);
                    }
                    return bodies(out);
                })
                .method("withTag", "(tag: string) -> { Instance }", a -> {
                    String tag = a.string(1);
                    return bodies(filtered(tree, null, body -> body.hasTag(tag)));
                })
                .method("random", "(except: Instance?) -> Instance?", a -> {
                    List<Found> all = bodies(tree, null, Double.POSITIVE_INFINITY, a.instance(1, null));
                    return all.isEmpty() ? null : all.get(ThreadLocalRandom.current().nextInt(all.size())).body();
                })
                .method("sortedByDistance", "(position: Vector3) -> { Instance }",
                        a -> bodies(bodies(tree, a.vector(1), Double.POSITIVE_INFINITY, null)))
                .method("inRange", "(a: Instance, b: Instance, range: number) -> boolean", a -> {
                    double range = a.number(3);
                    return position(a.instance(1)).sub(position(a.instance(2))).lengthSq() <= range * range;
                })
                .method("fromName", "(name: string) -> Instance?", a -> {
                    String name = a.string(1);
                    for (Character body : tree.ofClass(Classes.CHARACTER)) {
                        if (body.hasPlayer() && body.name().equalsIgnoreCase(name)) return body;
                    }
                    return null;
                })
                .method("count", "() -> number", a -> {
                    int n = 0;
                    for (Character body : tree.ofClass(Classes.CHARACTER)) if (body.hasPlayer()) n++;
                    return (double) n;
                });
        RosterRef roster = host.roster();
        if (roster != null) {
            players.method("list", "() -> { Player }", a -> {
                List<Object> out = new ArrayList<>();
                for (PlayerRef player : roster.all()) out.add(wrap(host, player));
                return out;
            });
            players.method("byId", "(id: string) -> Player?", a -> {
                PlayerRef player = roster.find(a.string(1));
                return player == null ? null : wrap(host, player);
            });
            players.method("playerOf", "(body: Instance) -> Player?", a -> {
                if (!(a.get(1) instanceof Character body) || !body.hasPlayer()) return null;
                PlayerRef player = roster.find(body.owner);
                return player == null ? null : wrap(host, player);
            });
        }
        SpawnRef spawns = host.spawns();
        if (spawns != null) {
            players.field("autoSpawn", "boolean", spawns::autoSpawn, value -> {
                if (!(value instanceof Boolean on)) throw new HostError("players.autoSpawn expects true or false");
                spawns.autoSpawn(on);
            });
            players.field("respawnTime", "number", spawns::respawnTime, value -> {
                if (!(value instanceof Number seconds) || seconds.doubleValue() < 0) throw new HostError("players.respawnTime expects seconds, 0 or more");
                spawns.respawnTime(seconds.doubleValue());
            });
        }
        if (host.controls() != null) players.value("controls", "PlayerControls", controls(host.controls()));
        if (host.camera() != null) {
            players.method("me", "() -> Instance?", a -> {
                Instance character = host.own().get();
                return character == null || !character.isAlive() ? null : character;
            });
        }
        host.declare(players);
        game.value("players", "Players", players);

        Members bodies = host.instances().of(Classes.CHARACTER);
        bodies.declare("humanoid", "Humanoid");
        bodies.method("applyImpulse", "(change: Vector3) -> ()", a -> {
            push(host, a.self(Character.class), a.vector(1), false);
            return null;
        });
        bodies.method("setVelocity", "(velocity: Vector3) -> ()", a -> {
            push(host, a.self(Character.class), a.vector(1), true);
            return null;
        });
        bodies.method("distanceTo", "(other: Instance) -> number", a -> position(a.self()).distance(position(a.instance(1))));
        bodies.method("distanceSqTo", "(other: Instance) -> number", a -> position(a.self()).sub(position(a.instance(1))).lengthSq());
        bodies.method("canSee", "(other: Instance, range: number?) -> boolean", a -> {
            Instance me = a.self(Character.class);
            Instance them = a.instance(1);
            Vector3 from = eye(me);
            Vector3 to = eye(them);
            if (a.has(2) && from.sub(to).lengthSq() > Math.pow(a.number(2), 2)) return false;
            return WorldQueries.clear(host, host.world(), from, to, List.of(me, them));
        });
        bodies.method("isGrounded", "() -> boolean", a -> {
            HumanoidState state = stateOf(a.self(Character.class));
            return state == HumanoidState.STANDING || state == HumanoidState.RUNNING || state == HumanoidState.SEATED;
        });
        bodies.method("isInWater", "() -> boolean", a -> {
            Character me = a.self(Character.class);
            return me.inWater || stateOf(me) == HumanoidState.SWIMMING;
        });
        bodies.method("isMoving", "() -> boolean", a -> {
            Character me = a.self(Character.class);
            return me.velocity.lengthSq() > 0.01 || me.moveSpeed > 0.05 || stateOf(me) == HumanoidState.RUNNING;
        });
        bodies.method("lookDirection", "() -> Vector3", a -> look(a.self(Character.class)));
        bodies.method("facing", "(other: Instance, maxAngle: number?) -> boolean", a -> {
            Character me = a.self(Character.class);
            Vector3 to = position(a.instance(1)).sub(position(me));
            to = new Vector3(to.x(), 0, to.z());
            Vector3 look = look(me);
            look = new Vector3(look.x(), 0, look.z());
            if (to.lengthSq() < 1e-9 || look.lengthSq() < 1e-9) return true;
            return to.normalize().dot(look.normalize()) >= Math.cos(Math.toRadians(a.number(2, 45)));
        });

        if (host.lens() != null) {
            CameraRef lens = host.lens();
            Members cameras = host.instances().of(Classes.CAMERA);
            cameras.method("shake", "(trauma: number) -> ()", a -> {
                lens.shake(a.number(1));
                return null;
            });
            cameras.method("kick", "(pitch: number, yaw: number, roll: number, seconds: number) -> ()", a -> {
                lens.kick(a.number(1), a.number(2), a.number(3), a.number(4));
                return null;
            });
            cameras.method("fovPunch", "(degrees: number, seconds: number) -> ()", a -> {
                lens.fovPunch(a.number(1), a.number(2));
                return null;
            });
            cameras.method("clearEffects", "() -> ()", a -> {
                lens.clearEffects();
                return null;
            });
            cameras.method("worldToScreen", "(world: Vector3) -> Vector3?", a -> lens.worldToScreen(a.vector(1)));
            cameras.method("screenToRay", "(x: number, y: number) -> (Vector3, Vector3)", a -> {
                CameraRef.Ray ray = lens.screenToRay(a.number(1), a.number(2));
                return Results.of(ray.origin(), ray.direction());
            });
            host.global("camera", "Camera", host.camera());
        }
        if (host.input() != null) {
            Members input = input(host.input());
            host.userInput(UserInput.install(host, input));
            host.global("input", "Input", input);
            host.declare(input);
        }
    }

    private static Members input(InputRef input) {
        Members members = new Members("Input")
                .field("mouseX", "number", input::mouseX)
                .field("mouseY", "number", input::mouseY)
                .field("mouseDeltaX", "number", input::mouseDeltaX)
                .field("mouseDeltaY", "number", input::mouseDeltaY)
                .field("screenWidth", "number", input::screenWidth)
                .field("screenHeight", "number", input::screenHeight)
                .field("mouseLocked", "boolean", input::mouseLocked)
                .field("sensitivity", "number", input::sensitivity, value -> {
                    if (!(value instanceof Number n)) throw new HostError("sensitivity expects a number");
                    input.sensitivity(n.doubleValue());
                })
                .method("down", "(action: string) -> boolean", a -> {
                    String action = a.string(1);
                    if (!input.known(action)) throw new HostError("'%s' is not an action", action);
                    return input.down(action);
                })
                .method("lockMouse", "() -> ()", a -> {
                    input.lockMouse(true);
                    return null;
                })
                .method("releaseMouse", "() -> ()", a -> {
                    input.lockMouse(false);
                    return null;
                });
        return members;
    }

    public static Vector3 look(Character body) {
        Quat turn = Transforms.world(body).rotation()
                .mul(Quat.axisAngle(Vector3.UP, -body.lookYaw))
                .mul(Quat.axisAngle(Vector3.RIGHT, -body.lookPitch));
        return turn.rotate(Vector3.FORWARD);
    }

    private static HumanoidState stateOf(Character body) {
        Humanoid living = Rig.humanoid(body);
        return living == null ? HumanoidState.STANDING : living.state;
    }

    private static List<Found> bodies(InstanceTree tree, Vector3 at, double radius, Instance except) {
        double limit = radius * radius;
        List<Found> out = new ArrayList<>();
        for (Character body : tree.ofClass(Classes.CHARACTER)) {
            if (!body.hasPlayer() || body == except || !body.isAlive()) continue;
            double d = 0;
            if (at != null) {
                d = Transforms.world(body).position().sub(at).lengthSq();
                if (d > limit) continue;
            }
            out.add(new Found(body, d));
        }
        if (at != null) out.sort((x, y) -> Double.compare(x.distanceSq(), y.distanceSq()));
        return out;
    }

    private static List<Found> filtered(InstanceTree tree, Instance except, Predicate<Character> keep) {
        List<Found> out = new ArrayList<>();
        for (Character body : tree.ofClass(Classes.CHARACTER)) {
            if (body.hasPlayer() && body != except && body.isAlive() && keep.test(body)) out.add(new Found(body, 0));
        }
        return out;
    }

    private static List<Object> bodies(List<Found> found) {
        List<Object> out = new ArrayList<>(found.size());
        for (Found one : found) out.add(one.body());
        return out;
    }

    public static Vector3 position(Instance instance) {
        return Transforms.world(instance).position();
    }

    public static Vector3 eye(Instance instance) {
        Vector3 at = position(instance);
        return instance instanceof Character body ? at.add(new Vector3(0, body.height * body.scale * 0.9, 0)) : at;
    }

    private static void push(Host host, Character body, Vector3 perSecond, boolean replace) {
        if (host.push() == null) throw new HostError("bodies can not be pushed here");
        if (!Double.isFinite(perSecond.x()) || !Double.isFinite(perSecond.y()) || !Double.isFinite(perSecond.z())) {
            throw new HostError("a push needs finite numbers, got %s", perSecond);
        }
        host.push().push(body, perSecond, replace);
    }
}
