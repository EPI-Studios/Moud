package com.meekdev.moud.core.nav;

import static com.meekdev.moud.core.nav.TileGrid.TILE_SIZE;
import static com.meekdev.moud.core.nav.TileGrid.tileIndex;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.TileCache.TileKey;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.recast4j.detour.QueryFilter;

public final class NavMeshes {

    public interface World {
        BlockShape block(int x, int y, int z);

        record Box(CFrame frame, Vector3 size, boolean solid, String label) {
            public Box(CFrame frame, Vector3 size) {
                this(frame, size, true, null);
            }
        }

        record Link(Vector3 from, Vector3 to, boolean both, String label) {}

        List<Box> boxes(Vector3 min, Vector3 max);

        default List<Link> links(Vector3 min, Vector3 max) {
            return List.of();
        }
    }

    private static final int MAX_TILES_PER_QUERY = 400;
    private static final int CORRIDOR_MARGIN_TILES = 2;

    private final AreaLabels areas;
    private final TileCache tiles;
    private final PathSearch paths;

    public NavMeshes() {
        this(Agent.PLAYER.shape());
    }

    public NavMeshes(Agent.Shape shape) {
        areas = new AreaLabels(this::markEveryTileDirty);
        TileBuilder builder = new TileBuilder(shape, areas);
        tiles = new TileCache(builder);
        paths = new PathSearch(builder.mesh(), areas);
    }

    private void markEveryTileDirty() {
        tiles.markAllDirty();
    }

    public synchronized void blockChanged(int x, int y, int z) {
        tiles.markChangedAround(x, z);
    }

    public List<Vector3> find(World world, Vector3 from, Vector3 to, boolean partial) {
        Route route = route(world, from, to, Agent.PLAYER);
        boolean usable = route.status() == Route.Status.SUCCESS || partial && route.status() == Route.Status.CLOSEST;
        return usable ? route.positions() : null;
    }

    public synchronized Route route(World world, Vector3 from, Vector3 to, Agent agent) {
        if (!finite(from) || !finite(to)) return Route.NO_PATH;
        QueryFilter filter = areas.costsFor(agent);
        Route best = null;
        for (int reach = 1; ; reach += 2) {
            Set<TileKey> wanted = tilesAlong(from, to, reach);
            if (wanted == null) return best != null ? best : Route.TOO_FAR;
            tiles.prepare(world, wanted, from, to);
            if (reach == 1 && !paths.onMesh(from, filter)) return Route.NO_PATH;
            Route found = paths.search(from, to, filter, agent);
            if (found.status() == Route.Status.SUCCESS) return found;
            if (found.found()) best = found;
            if (corridorCovered(from, to, reach)) break;
        }
        return best != null ? best : Route.NO_PATH;
    }

    public boolean clear(World world, Vector3 from, Vector3 to) {
        return blocked(world, from, List.of(to), Agent.PLAYER) < 0;
    }

    public int blocked(World world, Vector3 from, List<Vector3> ahead) {
        return blocked(world, from, ahead, Agent.PLAYER);
    }

    public synchronized int blocked(World world, Vector3 from, List<Vector3> ahead, Agent agent) {
        Set<TileKey> wanted = new HashSet<>();
        Vector3 at = from;
        for (Vector3 point : ahead) {
            Set<TileKey> along = tilesAlong(at, point, 1);
            if (along == null) return -1;
            wanted.addAll(along);
            at = point;
        }
        if (wanted.size() > MAX_TILES_PER_QUERY) return -1;
        double lowest = from.y();
        double highest = from.y();
        for (Vector3 point : ahead) {
            lowest = Math.min(lowest, point.y());
            highest = Math.max(highest, point.y());
        }
        tiles.prepare(world, wanted, new Vector3(from.x(), lowest, from.z()), new Vector3(from.x(), highest, from.z()));
        QueryFilter filter = areas.costsFor(agent);
        at = from;
        for (int i = 0; i < ahead.size(); i++) {
            if (!paths.segmentOpen(at, ahead.get(i), filter)) return i;
            at = ahead.get(i);
        }
        return -1;
    }

    public Vector3 randomNear(World world, Vector3 centre, double range, Random random) {
        return randomNear(world, centre, range, random, Agent.PLAYER);
    }

    public synchronized Vector3 randomNear(World world, Vector3 centre, double range, Random random, Agent agent) {
        if (!(range >= 0) || range > MAX_TILES_PER_QUERY * TILE_SIZE) return null;
        Set<TileKey> wanted = tilesAlong(centre, centre, (int) Math.ceil(range / TILE_SIZE));
        if (wanted == null) return null;
        tiles.prepare(world, wanted, centre, centre);
        QueryFilter filter = areas.costsFor(agent);
        return paths.randomPointNear(centre, range, filter, random);
    }

    public synchronized int builtTiles() {
        return tiles.builtCount();
    }

    private static boolean finite(Vector3 v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }

    private static Set<TileKey> tilesAlong(Vector3 a, Vector3 b, int reach) {
        if (!finite(a) || !finite(b) || (2L * reach + 1) * (2L * reach + 1) > MAX_TILES_PER_QUERY) return null;
        Set<TileKey> keys = new HashSet<>();
        double length = Math.hypot(b.x() - a.x(), b.z() - a.z());
        if (length > MAX_TILES_PER_QUERY * TILE_SIZE) return null;
        int steps = Math.max(1, (int) Math.ceil(length / (TILE_SIZE / 2)));
        for (int step = 0; step <= steps; step++) {
            double t = (double) step / steps;
            int tileX = tileIndex(a.x() + (b.x() - a.x()) * t);
            int tileZ = tileIndex(a.z() + (b.z() - a.z()) * t);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) keys.add(new TileKey(tileX + dx, tileZ + dz));
            }
            if (keys.size() > MAX_TILES_PER_QUERY) return null;
        }
        return keys;
    }

    private static boolean corridorCovered(Vector3 a, Vector3 b, int reach) {
        int firstX = tileIndex(Math.min(a.x(), b.x())) - CORRIDOR_MARGIN_TILES;
        int lastX = tileIndex(Math.max(a.x(), b.x())) + CORRIDOR_MARGIN_TILES;
        int firstZ = tileIndex(Math.min(a.z(), b.z())) - CORRIDOR_MARGIN_TILES;
        int lastZ = tileIndex(Math.max(a.z(), b.z())) + CORRIDOR_MARGIN_TILES;
        return reach >= Math.max(lastX - firstX, lastZ - firstZ);
    }
}
