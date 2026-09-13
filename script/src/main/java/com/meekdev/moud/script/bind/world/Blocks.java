package com.meekdev.moud.script.bind.world;

import com.meekdev.moud.script.bind.LuaTables;
import net.hollowcube.luau.LuaType;
import com.meekdev.moud.script.err.ScriptError;
import java.util.function.Consumer;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.script.api.BlockRef;
import net.hollowcube.luau.LuaState;
import com.meekdev.moud.script.bind.Signals;
import com.meekdev.moud.script.bind.Values;

public final class Blocks {

    public static final long MOST = 4_000_000;

    private Blocks() {}

    public static void install(LuaState state, BlockRef blocks) {
        state.getGlobal("game");
        state.newTable();
        LuaTables.function(state, "blocks", "get", s -> {
            Vector3 at = Values.vec3(s, 2);
            s.pushString(blocks.get(floor(at.x()), floor(at.y()), floor(at.z())));
            return 1;
        });
        LuaTables.function(state, "blocks", "set", s -> {
            writable(s, blocks);
            Vector3 at = Values.vec3(s, 2);
            try {
                blocks.set(floor(at.x()), floor(at.y()), floor(at.z()), s.checkString(3));
            } catch (IllegalArgumentException e) {
                throw s.error("%s", e.getMessage());
            }
            return 0;
        });
        LuaTables.function(state, "blocks", "fill", s -> {
            writable(s, blocks);
            Vector3 a = Values.vec3(s, 2);
            Vector3 b = Values.vec3(s, 3);
            int x0 = floor(Math.min(a.x(), b.x())), y0 = floor(Math.min(a.y(), b.y())), z0 = floor(Math.min(a.z(), b.z()));
            int x1 = floor(Math.max(a.x(), b.x())), y1 = floor(Math.max(a.y(), b.y())), z1 = floor(Math.max(a.z(), b.z()));
            long count = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
            if (count > MOST) throw s.error("fill of %d blocks exceeds the limit of %d", count, MOST);
            try {
                s.pushNumber(blocks.fill(x0, y0, z0, x1, y1, z1, s.checkString(4)));
            } catch (IllegalArgumentException e) {
                throw s.error("%s", e.getMessage());
            }
            return 1;
        });
        LuaTables.function(state, "blocks", "raycast", s -> {
            Vector3 from = Values.vec3(s, 2);
            Vector3 direction = Values.vec3(s, 3);
            double range = s.isNoneOrNil(4) ? 100 : s.checkNumber(4);
            boolean fluids = false;
            if (s.type(5) == LuaType.TABLE) {
                s.getField(5, "fluids");
                fluids = s.toBoolean(-1);
                s.pop(1);
            }
            BlockRef.Hit hit = blocks.raycast(from, direction, range, fluids);
            if (hit == null) {
                s.pushNil();
                return 1;
            }
            s.pushString(hit.block());
            Values.push(s, hit.at());
            s.pushNumber(hit.distance());
            Values.push(s, hit.normal());
            return 4;
        });
        LuaTables.function(state, "blocks", "isSolid", s -> at(s, blocks::solid));
        LuaTables.function(state, "blocks", "isAir", s -> at(s, blocks::air));
        LuaTables.function(state, "blocks", "isFluid", s -> at(s, blocks::fluid));
        LuaTables.function(state, "blocks", "lightAt", s -> {
            Vector3 at = Values.vec3(s, 2);
            s.pushNumber(blocks.light(floor(at.x()), floor(at.y()), floor(at.z())));
            return 1;
        });
        LuaTables.function(state, "blocks", "topAt", s -> {
            s.pushNumber(blocks.top(floor(s.checkNumber(2)), floor(s.checkNumber(3))));
            return 1;
        });
        LuaTables.function(state, "blocks", "find", s -> {
            String id = s.checkString(2);
            Vector3 centre = Values.vec3(s, 3);
            int radius = (int) Math.ceil(s.checkNumber(4));
            int limit = s.isNoneOrNil(5) ? Integer.MAX_VALUE : (int) s.checkNumber(5);
            if (radius > 64) throw s.error("find radius %d exceeds the limit of 64", radius);
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
            found.sort((a, b) -> Double.compare(a.sub(centre).lengthSq(), b.sub(centre).lengthSq()));
            s.createTable(Math.min(found.size(), limit), 0);
            for (int n = 0; n < found.size() && n < limit; n++) {
                Values.push(s, found.get(n));
                s.rawSetI(-2, n + 1);
            }
            return 1;
        });
        LuaTables.function(state, "blocks", "count", s -> {
            String id = s.checkString(2);
            Box box = box(s, 3, 4);
            long count = 0;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        if (blocks.id(x, y, z).equals(id) || blocks.get(x, y, z).equals(id)) count++;
                    }
                }
            }
            s.pushNumber(count);
            return 1;
        });
        LuaTables.function(state, "blocks", "replace", s -> {
            writable(s, blocks);
            String from = s.checkString(2);
            String to = s.checkString(3);
            Box box = box(s, 4, 5);
            long count = 0;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        if (!blocks.id(x, y, z).equals(from) && !blocks.get(x, y, z).equals(from)) continue;
                        set(s, blocks, x, y, z, to);
                        count++;
                    }
                }
            }
            s.pushNumber(count);
            return 1;
        });
        LuaTables.function(state, "blocks", "sphere", s -> {
            writable(s, blocks);
            Vector3 centre = Values.vec3(s, 2);
            double radius = s.checkNumber(3);
            String block = s.checkString(4);
            boolean hollow = s.toBoolean(5);
            int r = (int) Math.ceil(radius);
            long count = 0;
            int cx = floor(centre.x()), cy = floor(centre.y()), cz = floor(centre.z());
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        double d = Math.sqrt(x * x + y * y + z * z);
                        if (d > radius || hollow && d < radius - 1) continue;
                        set(s, blocks, cx + x, cy + y, cz + z, block);
                        count++;
                    }
                }
            }
            s.pushNumber(count);
            return 1;
        });
        LuaTables.function(state, "blocks", "cylinder", s -> {
            writable(s, blocks);
            Vector3 base = Values.vec3(s, 2);
            double radius = s.checkNumber(3);
            int height = (int) s.checkNumber(4);
            String block = s.checkString(5);
            boolean hollow = s.toBoolean(6);
            int r = (int) Math.ceil(radius);
            long count = 0;
            int cx = floor(base.x()), cy = floor(base.y()), cz = floor(base.z());
            for (int y = 0; y < height; y++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        double d = Math.sqrt(x * x + z * z);
                        if (d > radius || hollow && d < radius - 1) continue;
                        set(s, blocks, cx + x, cy + y, cz + z, block);
                        count++;
                    }
                }
            }
            s.pushNumber(count);
            return 1;
        });
        LuaTables.function(state, "blocks", "line", s -> {
            writable(s, blocks);
            Vector3 a = Values.vec3(s, 2);
            Vector3 b = Values.vec3(s, 3);
            String block = s.checkString(4);
            int steps = (int) Math.ceil(Math.max(Math.abs(b.x() - a.x()), Math.max(Math.abs(b.y() - a.y()), Math.abs(b.z() - a.z()))));
            long count = 0;
            int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
            for (int n = 0; n <= steps; n++) {
                Vector3 at = steps == 0 ? a : a.lerp(b, (double) n / steps);
                int x = floor(at.x()), y = floor(at.y()), z = floor(at.z());
                if (x == lx && y == ly && z == lz) continue;
                set(s, blocks, x, y, z, block);
                lx = x;
                ly = y;
                lz = z;
                count++;
            }
            s.pushNumber(count);
            return 1;
        });
        LuaTables.function(state, "blocks", "hollowBox", s -> {
            writable(s, blocks);
            Box box = box(s, 2, 3);
            String block = s.checkString(4);
            long count = 0;
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        boolean edge = x == box.minX() || x == box.maxX() || y == box.minY() || y == box.maxY() || z == box.minZ() || z == box.maxZ();
                        if (!edge) continue;
                        set(s, blocks, x, y, z, block);
                        count++;
                    }
                }
            }
            s.pushNumber(count);
            return 1;
        });
        LuaTables.function(state, "blocks", "copy", s -> {
            Box box = box(s, 2, 3);
            List<String> palette = new ArrayList<>();
            Map<String, Integer> index = new HashMap<>();
            List<Integer> cells = new ArrayList<>();
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
                        cells.add(at);
                    }
                }
            }
            s.createTable(0, 3);
            Values.push(s, new Vector3(box.maxX() - box.minX() + 1, box.maxY() - box.minY() + 1, box.maxZ() - box.minZ() + 1));
            s.rawSetField(-2, "size");
            s.createTable(palette.size(), 0);
            for (int n = 0; n < palette.size(); n++) {
                s.pushString(palette.get(n));
                s.rawSetI(-2, n + 1);
            }
            s.rawSetField(-2, "palette");
            s.createTable(cells.size(), 0);
            for (int n = 0; n < cells.size(); n++) {
                s.pushNumber(cells.get(n));
                s.rawSetI(-2, n + 1);
            }
            s.rawSetField(-2, "blocks");
            return 1;
        });
        LuaTables.function(state, "blocks", "paste", s -> {
            writable(s, blocks);
            if (s.type(2) != LuaType.TABLE) throw s.error("paste expects what copy returned");
            Vector3 at = Values.vec3(s, 3);
            int turns = s.isNoneOrNil(4) ? 0 : (int) s.checkNumber(4);
            boolean skipAir = s.toBoolean(5);
            s.getField(2, "size");
            Vector3 size = Values.vec3(s, -1);
            s.pop(1);
            s.getField(2, "palette");
            int paletteAt = s.top();
            List<String> palette = new ArrayList<>();
            for (int n = 1; n <= s.len(paletteAt); n++) {
                s.rawGetI(paletteAt, n);
                palette.add(blocks.rotate(s.toString(-1), turns));
                s.pop(1);
            }
            s.pop(1);
            s.getField(2, "blocks");
            int cellsAt = s.top();
            int sx = (int) size.x(), sy = (int) size.y(), sz = (int) size.z();
            int ox = floor(at.x()), oy = floor(at.y()), oz = floor(at.z());
            long count = 0;
            int n = 1;
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++, n++) {
                        s.rawGetI(cellsAt, n);
                        int cell = (int) s.toNumber(-1);
                        s.pop(1);
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
                        set(s, blocks, ox + rx, oy + y, oz + rz, block);
                        count++;
                    }
                }
            }
            s.pop(1);
            s.pushNumber(count);
            return 1;
        });
        Signals.Handlers changed = new Signals.Handlers();
        CHANGED.put(state.mainThread(), new Watch(blocks, changed));
        Signals.push(state, changed);
        state.rawSetField(-2, "changed");
        state.rawSetField(-2, "blocks");
        state.pop(1);
    }

    private record Watch(BlockRef blocks, Signals.Handlers changed) {}

    private static final Map<LuaState, Watch> CHANGED = new HashMap<>();

    public static void drain(LuaState state, Instance world, Consumer<ScriptError> onError) {
        Watch watch = CHANGED.get(state.mainThread());
        if (watch == null) return;
        watch.blocks().drainChanges(change -> {
            Paths.blockChanged(world, change.x(), change.y(), change.z());
            Signals.fire(state, watch.changed(), onError, s -> {
                Values.push(s, new Vector3(change.x(), change.y(), change.z()));
                s.pushString(change.block());
                return 2;
            });
        });
    }

    public static void forget(LuaState state) {
        CHANGED.remove(state.mainThread());
    }

    private interface Test {
        boolean at(int x, int y, int z);
    }

    private static int at(LuaState s, Test test) {
        Vector3 at = Values.vec3(s, 2);
        s.pushBoolean(test.at(floor(at.x()), floor(at.y()), floor(at.z())));
        return 1;
    }

    private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    private static Box box(LuaState s, int first, int second) {
        Vector3 a = Values.vec3(s, first);
        Vector3 b = Values.vec3(s, second);
        Box box = new Box(floor(Math.min(a.x(), b.x())), floor(Math.min(a.y(), b.y())), floor(Math.min(a.z(), b.z())),
                floor(Math.max(a.x(), b.x())), floor(Math.max(a.y(), b.y())), floor(Math.max(a.z(), b.z())));
        long count = (long) (box.maxX() - box.minX() + 1) * (box.maxY() - box.minY() + 1) * (box.maxZ() - box.minZ() + 1);
        if (count > MOST) throw s.error("%d blocks exceeds the limit of %d", count, MOST);
        return box;
    }

    private static void set(LuaState s, BlockRef blocks, int x, int y, int z, String block) {
        try {
            blocks.set(x, y, z, block);
        } catch (IllegalArgumentException e) {
            throw s.error("%s", e.getMessage());
        }
    }

    private static void writable(LuaState state, BlockRef blocks) {
        if (!blocks.writable()) throw state.error("blocks are read-only on the client");
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

}
