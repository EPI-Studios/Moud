package com.meekdev.moud.script.host.world;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.script.api.BlockRef;
import com.meekdev.moud.script.api.FileRef;
import com.meekdev.moud.script.api.HistoryRef;
import com.meekdev.moud.script.host.Args;
import com.meekdev.moud.script.host.Callable;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.host.HostError;
import com.meekdev.moud.script.host.HostSignal;
import com.meekdev.moud.script.host.Members;
import com.meekdev.moud.script.host.Results;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Blocks {

    public static final long MOST = 4_000_000;

    private interface Test {
        boolean at(int x, int y, int z);
    }

    private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    private Blocks() {}

    public static void install(Host host, Members game) {
        installBlocks(host, game);
        installHistory(host, game);
        installScenes(host);
    }

    private static void installBlocks(Host host, Members game) {
        BlockRef blocks = host.blocks();
        if (blocks == null) return;
        host.api().alias("BlockCopy", "{ size: Vector3, palette: { string }, blocks: { number } }");
        HostSignal changed = new HostSignal(host, "BlockChangedSignal", "blocks.changed");
        host.api().declare(HostSignal.decl("BlockChangedSignal", "(at: Vector3, block: string) -> ()"));
        Runnable drain = () -> blocks.drainChanges(change -> {
            Paths.blockChanged(host.world(), change.x(), change.y(), change.z());
            changed.fire(new Vector3(change.x(), change.y(), change.z()), change.block());
        });
        if (host.client()) host.onRenderStep(dt -> drain.run()); else host.onStep(dt -> drain.run());

        Members members = new Members("Blocks")
                .method("get", "(at: Vector3) -> string", a -> {
                    Vector3 at = a.vector(1);
                    return blocks.get(floor(at.x()), floor(at.y()), floor(at.z()));
                })
                .method("set", "(at: Vector3, block: string) -> ()", a -> {
                    writable(blocks);
                    Vector3 at = a.vector(1);
                    set(blocks, floor(at.x()), floor(at.y()), floor(at.z()), a.string(2));
                    return null;
                })
                .method("fill", "(from: Vector3, to: Vector3, block: string) -> number", a -> {
                    writable(blocks);
                    Box box = box(a, 1, 2, "fill of %d blocks exceeds the limit of %d");
                    try {
                        return (double) blocks.fill(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), a.string(3));
                    } catch (IllegalArgumentException e) {
                        throw new HostError(e.getMessage());
                    }
                })
                .method("raycast", "(from: Vector3, direction: Vector3, range: number?, options: { fluids: boolean? }?) -> (string?, Vector3?, number?, Vector3?)", a -> {
                    boolean fluids = Boolean.TRUE.equals(a.map(4, Map.of()).get("fluids"));
                    BlockRef.Hit hit = blocks.raycast(a.vector(1), a.vector(2), a.number(3, 100), fluids);
                    return hit == null ? null : Results.of(hit.block(), hit.at(), hit.distance(), hit.normal());
                })
                .method("isSolid", "(at: Vector3) -> boolean", a -> at(a, blocks::solid))
                .method("isAir", "(at: Vector3) -> boolean", a -> at(a, blocks::air))
                .method("isFluid", "(at: Vector3) -> boolean", a -> at(a, blocks::fluid))
                .method("lightAt", "(at: Vector3) -> number", a -> {
                    Vector3 at = a.vector(1);
                    return (double) blocks.light(floor(at.x()), floor(at.y()), floor(at.z()));
                })
                .method("topAt", "(x: number, z: number) -> number", a -> (double) blocks.top(floor(a.number(1)), floor(a.number(2))))
                .method("find", "(block: string, centre: Vector3, radius: number, limit: number?) -> { Vector3 }", a -> {
                    String id = a.string(1);
                    Vector3 centre = a.vector(2);
                    int radius = (int) Math.ceil(a.number(3));
                    int limit = a.integer(4, Integer.MAX_VALUE);
                    if (radius > 64) throw new HostError("find radius %d exceeds the limit of 64", radius);
                    List<Vector3> found = new ArrayList<>();
                    int cx = floor(centre.x()), cy = floor(centre.y()), cz = floor(centre.z());
                    for (int x = cx - radius; x <= cx + radius; x++) {
                        for (int y = cy - radius; y <= cy + radius; y++) {
                            for (int z = cz - radius; z <= cz + radius; z++) {
                                Vector3 middle = new Vector3(x + 0.5, y + 0.5, z + 0.5);
                                if (middle.sub(centre).lengthSq() > (double) radius * radius) continue;
                                if (blocks.id(x, y, z).equals(id) || blocks.get(x, y, z).equals(id)) found.add(middle);
                            }
                        }
                    }
                    found.sort((p, q) -> Double.compare(p.sub(centre).lengthSq(), q.sub(centre).lengthSq()));
                    return new ArrayList<Object>(found.subList(0, Math.min(found.size(), limit)));
                })
                .method("count", "(block: string, from: Vector3, to: Vector3) -> number", a -> {
                    String id = a.string(1);
                    Box box = box(a, 2, 3, "%d blocks exceeds the limit of %d");
                    long count = 0;
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        for (int y = box.minY(); y <= box.maxY(); y++) {
                            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                                if (blocks.id(x, y, z).equals(id) || blocks.get(x, y, z).equals(id)) count++;
                            }
                        }
                    }
                    return (double) count;
                })
                .method("replace", "(from: string, to: string, a: Vector3, b: Vector3) -> number", a -> {
                    writable(blocks);
                    String from = a.string(1);
                    String to = a.string(2);
                    Box box = box(a, 3, 4, "%d blocks exceeds the limit of %d");
                    long count = 0;
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        for (int y = box.minY(); y <= box.maxY(); y++) {
                            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                                if (!blocks.id(x, y, z).equals(from) && !blocks.get(x, y, z).equals(from)) continue;
                                set(blocks, x, y, z, to);
                                count++;
                            }
                        }
                    }
                    return (double) count;
                })
                .method("sphere", "(centre: Vector3, radius: number, block: string, hollow: boolean?) -> number", a -> {
                    writable(blocks);
                    Vector3 centre = a.vector(1);
                    double radius = a.number(2);
                    String block = a.string(3);
                    boolean hollow = a.truthy(4);
                    int r = (int) Math.ceil(radius);
                    long count = 0;
                    int cx = floor(centre.x()), cy = floor(centre.y()), cz = floor(centre.z());
                    for (int x = -r; x <= r; x++) {
                        for (int y = -r; y <= r; y++) {
                            for (int z = -r; z <= r; z++) {
                                double d = Math.sqrt(x * x + y * y + z * z);
                                if (d > radius || hollow && d < radius - 1) continue;
                                set(blocks, cx + x, cy + y, cz + z, block);
                                count++;
                            }
                        }
                    }
                    return (double) count;
                })
                .method("cylinder", "(base: Vector3, radius: number, height: number, block: string, hollow: boolean?) -> number", a -> {
                    writable(blocks);
                    Vector3 base = a.vector(1);
                    double radius = a.number(2);
                    int height = a.integer(3);
                    String block = a.string(4);
                    boolean hollow = a.truthy(5);
                    int r = (int) Math.ceil(radius);
                    long count = 0;
                    int cx = floor(base.x()), cy = floor(base.y()), cz = floor(base.z());
                    for (int y = 0; y < height; y++) {
                        for (int x = -r; x <= r; x++) {
                            for (int z = -r; z <= r; z++) {
                                double d = Math.sqrt(x * x + z * z);
                                if (d > radius || hollow && d < radius - 1) continue;
                                set(blocks, cx + x, cy + y, cz + z, block);
                                count++;
                            }
                        }
                    }
                    return (double) count;
                })
                .method("line", "(from: Vector3, to: Vector3, block: string) -> number", a -> {
                    writable(blocks);
                    Vector3 from = a.vector(1);
                    Vector3 to = a.vector(2);
                    String block = a.string(3);
                    int steps = (int) Math.ceil(Math.max(Math.abs(to.x() - from.x()), Math.max(Math.abs(to.y() - from.y()), Math.abs(to.z() - from.z()))));
                    long count = 0;
                    int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
                    for (int n = 0; n <= steps; n++) {
                        Vector3 at = steps == 0 ? from : from.lerp(to, (double) n / steps);
                        int x = floor(at.x()), y = floor(at.y()), z = floor(at.z());
                        if (x == lx && y == ly && z == lz) continue;
                        set(blocks, x, y, z, block);
                        lx = x;
                        ly = y;
                        lz = z;
                        count++;
                    }
                    return (double) count;
                })
                .method("hollowBox", "(from: Vector3, to: Vector3, block: string) -> number", a -> {
                    writable(blocks);
                    Box box = box(a, 1, 2, "%d blocks exceeds the limit of %d");
                    String block = a.string(3);
                    long count = 0;
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        for (int y = box.minY(); y <= box.maxY(); y++) {
                            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                                boolean edge = x == box.minX() || x == box.maxX() || y == box.minY() || y == box.maxY() || z == box.minZ() || z == box.maxZ();
                                if (!edge) continue;
                                set(blocks, x, y, z, block);
                                count++;
                            }
                        }
                    }
                    return (double) count;
                })
                .method("copy", "(from: Vector3, to: Vector3) -> BlockCopy", a -> copy(blocks, box(a, 1, 2, "%d blocks exceeds the limit of %d")))
                .method("paste", "(copy: BlockCopy, at: Vector3, quarterTurns: number?, skipAir: boolean?) -> number", a -> paste(blocks, a))
                .value("changed", "BlockChangedSignal", changed);
        host.declare(members);
        game.value("blocks", "Blocks", members);
    }

    private static Object copy(BlockRef blocks, Box box) {
        List<Object> palette = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        List<Object> cells = new ArrayList<>();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    String block = blocks.get(x, y, z);
                    Integer at = index.get(block);
                    if (at == null) {
                        palette.add(block);
                        at = palette.size();
                        index.put(block, at);
                    }
                    cells.add((double) at);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("size", new Vector3(box.maxX() - box.minX() + 1, box.maxY() - box.minY() + 1, box.maxZ() - box.minZ() + 1));
        out.put("palette", palette);
        out.put("blocks", cells);
        return out;
    }

    private static Object paste(BlockRef blocks, Args a) {
        writable(blocks);
        Map<String, Object> copy = a.map(1);
        Vector3 at = a.vector(2);
        int turns = a.integer(3, 0);
        boolean skipAir = a.truthy(4);
        if (!(copy.get("size") instanceof Vector3 size) || !(copy.get("palette") instanceof List<?> rawPalette)
                || !(copy.get("blocks") instanceof List<?> cells)) {
            throw new HostError("paste expects what copy returned");
        }
        List<String> palette = new ArrayList<>();
        for (Object entry : rawPalette) palette.add(blocks.rotate(String.valueOf(entry), turns));
        int sx = (int) size.x(), sy = (int) size.y(), sz = (int) size.z();
        int ox = floor(at.x()), oy = floor(at.y()), oz = floor(at.z());
        long count = 0;
        int n = 0;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++, n++) {
                    int cell = n < cells.size() && cells.get(n) instanceof Number c ? c.intValue() : 0;
                    if (cell < 1 || cell > palette.size()) continue;
                    String block = palette.get(cell - 1);
                    if (skipAir && block.startsWith("minecraft:air")) continue;
                    int rx, rz;
                    switch (Math.floorMod(turns, 4)) {
                        case 1 -> { rx = sz - 1 - z; rz = x; }
                        case 2 -> { rx = sx - 1 - x; rz = sz - 1 - z; }
                        case 3 -> { rx = z; rz = sx - 1 - x; }
                        default -> { rx = x; rz = z; }
                    }
                    set(blocks, ox + rx, oy + y, oz + rz, block);
                    count++;
                }
            }
        }
        return (double) count;
    }

    private static void installHistory(Host host, Members game) {
        HistoryRef history = host.history();
        if (history == null) return;
        Members members = new Members("History")
                .method("now", "() -> number", a -> history.rewind().now())
                .method("viewTime", "(body: Instance) -> number", a -> {
                    if (!(a.get(1) instanceof Character body)) throw new HostError("history:viewTime expects a body");
                    return history.viewTime(body.owner);
                })
                .method("rewind", "(time: number, query: () -> ...any) -> ...any", a -> {
                    double seconds = a.number(1);
                    Callable query = a.callable(2);
                    Object[][] out = new Object[1][];
                    Queries.withFrames(part -> history.rewind().at(part, seconds), () -> {
                        out[0] = query.call();
                        return null;
                    });
                    return out[0] == null ? null : new Results(out[0]);
                });
        host.declare(members);
        game.value("history", "History", members);
    }

    private static void installScenes(Host host) {
        FileRef files = host.files();
        if (files == null) return;
        Members scene = new Members("SceneService")
                .function("load", "(path: string, parent: Instance?) -> { Instance }", a -> {
                    String path = a.string(0);
                    String text = files.read(path);
                    if (text == null) throw new HostError("there is no scene at %s", path);
                    return decode(host, text, a.instance(1, host.world()), path);
                })
                .function("decode", "(text: string, parent: Instance?) -> { Instance }",
                        a -> decode(host, a.string(0), a.instance(1, host.world()), "the text"))
                .function("save", "(instances: any, path: string) -> ()", a -> {
                    String path = a.string(1);
                    if (!path.endsWith(".scene")) throw new HostError("scene path must end in .scene, got %s", path);
                    try {
                        files.write(path, Scene.save(roots(a.get(0))));
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        throw new HostError(e.getMessage());
                    }
                    return null;
                })
                .function("encode", "(instances: any) -> string", a -> Scene.save(roots(a.get(0))));
        host.global("scene", "SceneService", scene);
        host.declare(scene);
    }

    private static Object decode(Host host, String text, Instance parent, String from) {
        try {
            return new ArrayList<Object>(Scene.load(text, parent, host.classes()));
        } catch (IllegalArgumentException e) {
            throw new HostError("%s: %s", from, e.getMessage());
        }
    }

    private static List<Instance> roots(Object value) {
        if (value instanceof Instance one) return List.of(one);
        if (!(value instanceof List<?> list)) throw new HostError("save expects an instance or a list of instances");
        List<Instance> out = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Instance instance)) throw new HostError("save expects an instance or a list of instances");
            out.add(instance);
        }
        return out;
    }

    private static Object at(Args a, Test test) {
        Vector3 at = a.vector(1);
        return test.at(floor(at.x()), floor(at.y()), floor(at.z()));
    }

    private static Box box(Args a, int first, int second, String limit) {
        Vector3 p = a.vector(first);
        Vector3 q = a.vector(second);
        Box box = new Box(floor(Math.min(p.x(), q.x())), floor(Math.min(p.y(), q.y())), floor(Math.min(p.z(), q.z())),
                floor(Math.max(p.x(), q.x())), floor(Math.max(p.y(), q.y())), floor(Math.max(p.z(), q.z())));
        long count = (long) (box.maxX() - box.minX() + 1) * (box.maxY() - box.minY() + 1) * (box.maxZ() - box.minZ() + 1);
        if (count > MOST) throw new HostError(limit, count, MOST);
        return box;
    }

    private static void set(BlockRef blocks, int x, int y, int z, String block) {
        try {
            blocks.set(x, y, z, block);
        } catch (IllegalArgumentException e) {
            throw new HostError(e.getMessage());
        }
    }

    private static void writable(BlockRef blocks) {
        if (!blocks.writable()) throw new HostError("blocks are read-only on the client");
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
