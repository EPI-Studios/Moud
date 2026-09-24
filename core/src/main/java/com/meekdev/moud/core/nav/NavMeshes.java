package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.recast4j.detour.FindNearestPolyResult;
import org.recast4j.detour.FindRandomPointResult;
import org.recast4j.detour.MeshData;
import org.recast4j.detour.MeshTile;
import org.recast4j.detour.NavMesh;
import org.recast4j.detour.NavMeshBuilder;
import org.recast4j.detour.NavMeshDataCreateParams;
import org.recast4j.detour.NavMeshParams;
import org.recast4j.detour.NavMeshQuery;
import org.recast4j.detour.Poly;
import org.recast4j.detour.QueryFilter;
import org.recast4j.detour.RaycastHit;
import org.recast4j.detour.Result;
import org.recast4j.detour.StraightPathItem;
import org.recast4j.detour.Tupple2;
import org.recast4j.recast.AreaModification;
import org.recast4j.recast.ConvexVolume;
import org.recast4j.recast.Heightfield;
import org.recast4j.recast.PolyMesh;
import org.recast4j.recast.PolyMeshDetail;
import org.recast4j.recast.Recast;
import org.recast4j.recast.RecastBuilder;
import org.recast4j.recast.RecastBuilderConfig;
import org.recast4j.recast.RecastConfig;
import org.recast4j.recast.RecastConstants;
import org.recast4j.recast.RecastRasterization;
import org.recast4j.recast.Telemetry;

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

    private static final float STEP = 0.6f;
    private static final float JUMP_CLIMB = 1.05f;
    private static final float SLOPE = 50;
    private static final float CELL = 0.25f;
    private static final float CELL_HEIGHT = 0.1f;
    private static final int CELLS_PER_BLOCK = Math.round(1 / CELL);
    private static final int CELLS_PER_LAYER = Math.round(1 / CELL_HEIGHT);
    private static final int TILE_CELLS = 128;
    private static final float TILE = TILE_CELLS * CELL;
    private static final int TILE_BLOCKS = Math.round(TILE);
    private static final float MAX_DROP = 3.2f;
    private static final int VERTS_PER_POLY = 6;
    private static final int REACH_Y = 16;
    private static final int MAX_TILES = 400;
    private static final int MAX_CORNERS = 1024;
    private static final int CORRIDOR_MARGIN = 2;
    private static final float[] EXTENTS = {1.5f, 3f, 1.5f};
    private static final float[] BELOW = {1.5f, REACH_Y, 1.5f};
    private static final double MIN_COST = 0.01;
    private static final double LAYER_SNAP = 1e-3;
    private static final float CELL_SNAP = 1e-3f;
    private static final double SPACING_SLACK = 1e-6;
    private static final double SNAP_REACH = 0.6;
    private static final double DETOUR = 1.05;
    private static final double DETOUR_EXTRA = 0.5;
    private static final double ARRIVE_ACROSS = 1.0;
    private static final double ARRIVE_HEIGHT = 2.5;
    private static final float LINK_SLACK = 0.3f;
    private static final float VOLUME_PAD = 0.25f;
    private static final long BOX_SPREAD = 0x9E3779B97F4A7C15L;
    private static final long LINK_SPREAD = 0xC2B2AE3D27D4EB4FL;

    private static final int GROUND_AREA = 1;
    private static final int WATER_AREA = 2;
    private static final int LAVA_AREA = 3;
    private static final int JUMP_AREA = 4;
    private static final int CLIMB_AREA = 5;

    private static final AreaModification WALKABLE = new AreaModification(GROUND_AREA);

    private record Key(int x, int z) {}

    private record Bounds(Vector3 min, Vector3 max) {}

    private static final class Tile {
        int minY;
        int maxY;
        long parts;
        boolean dirty;
        boolean built;

        void stretch(int low, int high) {
            minY = built ? Math.min(minY, low) : low;
            maxY = built ? Math.max(maxY, high) : high;
        }
    }

    private final Agent.Shape shape;
    private final float radius;
    private final float height;
    private final float climb;
    private final RecastConfig config;
    private final NavMesh mesh;
    private final NavMeshQuery query;
    private final Map<Key, Tile> tiles = new HashMap<>();
    private final Map<String, Integer> areas = new HashMap<>();
    private final String[] labels = new String[NavMesh.DT_MAX_AREAS];
    private final QueryFilter walkable;

    public NavMeshes() {
        this(Agent.PLAYER.shape());
    }

    public NavMeshes(Agent.Shape shape) {
        this.shape = shape;
        radius = (float) shape.radius();
        height = (float) shape.height();
        climb = shape.canJump() ? JUMP_CLIMB : STEP;
        config = recastConfig(radius, height, climb);
        NavMeshParams params = new NavMeshParams();
        params.tileWidth = TILE;
        params.tileHeight = TILE;
        params.maxTiles = 1 << 14;
        params.maxPolys = 1 << 14;
        mesh = new NavMesh(params, VERTS_PER_POLY);
        query = new NavMeshQuery(mesh);
        labels[WATER_AREA] = "water";
        labels[LAVA_AREA] = "lava";
        labels[JUMP_AREA] = "jump";
        labels[CLIMB_AREA] = "climb";
        for (int area = WATER_AREA; area <= CLIMB_AREA; area++) areas.put(labels[area], area);
        walkable = costs(Agent.PLAYER);
    }

    private static RecastConfig recastConfig(float radius, float height, float climb) {
        float erode = (float) (Math.floor(radius / CELL + 0.01) * CELL);
        float minRegion = 8 * 8 * CELL * CELL;
        float mergeRegion = 20 * 20 * CELL * CELL;
        return new RecastConfig(true, TILE_CELLS, TILE_CELLS, RecastConfig.calcBorder(radius, CELL),
                RecastConstants.PartitionType.WATERSHED, CELL, CELL_HEIGHT, SLOPE, true, true, true, height, erode, climb,
                minRegion, mergeRegion, 12, 1.3f, VERTS_PER_POLY, true, 6, 1, WALKABLE);
    }

    public synchronized void blockChanged(int x, int y, int z) {
        float border = config.borderSize * CELL + 1;
        for (int tx = tile(x - border); tx <= tile(x + border); tx++) {
            for (int tz = tile(z - border); tz <= tile(z + border); tz++) {
                Tile t = tiles.get(new Key(tx, tz));
                if (t != null) t.dirty = true;
            }
        }
    }

    private int area(String label) {
        if (label == null) return GROUND_AREA;
        Integer known = areas.get(label);
        return known == null ? GROUND_AREA : known;
    }

    private int register(String label) {
        if (label == null) return GROUND_AREA;
        Integer known = areas.get(label);
        if (known != null) return known;
        int next = areas.size() + WATER_AREA;
        if (next >= labels.length) return GROUND_AREA;
        areas.put(label, next);
        labels[next] = label;
        for (Tile tile : tiles.values()) tile.dirty = true;
        return next;
    }

    private QueryFilter costs(Agent agent) {
        float[] perArea = new float[NavMesh.DT_MAX_AREAS];
        Arrays.fill(perArea, 1);
        perArea[LAVA_AREA] = Float.POSITIVE_INFINITY;
        if (!agent.canJump()) perArea[JUMP_AREA] = Float.POSITIVE_INFINITY;
        if (!agent.canClimb()) perArea[CLIMB_AREA] = Float.POSITIVE_INFINITY;
        for (Map.Entry<String, Double> cost : agent.costs().entrySet()) {
            int area = register(cost.getKey());
            if (area == GROUND_AREA) continue;
            double value = cost.getValue();
            perArea[area] = Double.isNaN(value) ? 1 : (float) Math.max(MIN_COST, value);
        }
        return new Costs(perArea);
    }

    public List<Vector3> find(World world, Vector3 from, Vector3 to, boolean partial) {
        Route route = route(world, from, to, Agent.PLAYER);
        boolean usable = route.status() == Route.Status.SUCCESS || partial && route.status() == Route.Status.CLOSEST;
        return usable ? route.positions() : null;
    }

    public synchronized Route route(World world, Vector3 from, Vector3 to, Agent agent) {
        QueryFilter filter = costs(agent);
        Route best = null;
        for (int reach = 1; ; reach += 2) {
            Set<Key> wanted = tilesAlong(from, to, reach);
            if (wanted.size() > MAX_TILES) return best != null ? best : Route.TOO_FAR;
            prepare(world, wanted, from, to);
            Route found = search(from, to, filter, agent);
            if (found.status() == Route.Status.SUCCESS) return found;
            if (found.found()) best = found;
            if (covers(from, to, reach)) break;
        }
        return best != null ? best : Route.NO_PATH;
    }

    private Route search(Vector3 from, Vector3 to, QueryFilter filter, Agent agent) {
        FindNearestPolyResult start = nearest(from, EXTENTS, filter);
        if (start == null) return Route.NO_PATH;
        FindNearestPolyResult end = goal(from, to, filter);
        if (end == null) return Route.NO_PATH;
        List<Long> polys = polysBetween(start, end, filter);
        if (polys == null) return Route.NO_PATH;
        List<StraightPathItem> corners = corners(start, end, polys, NavMeshQuery.DT_STRAIGHTPATH_AREA_CROSSINGS);
        if (corners == null || corners.isEmpty()) return Route.NO_PATH;
        List<Waypoint> waypoints = waypoints(corners, agent.spacing(), filter);
        Vector3 last = waypoints.getLast().position();
        return new Route(reached(last, to) ? Route.Status.SUCCESS : Route.Status.CLOSEST, waypoints);
    }

    private FindNearestPolyResult goal(Vector3 from, Vector3 to, QueryFilter filter) {
        FindNearestPolyResult end = nearest(to, EXTENTS, filter);
        if (end == null) end = nearest(to, BELOW, filter);
        if (end != null) return end;
        Vector3 toward = to.sub(from);
        Vector3 withinATile = from.add(toward.normalize().mul(Math.min(toward.length(), TILE)));
        return nearest(withinATile, EXTENTS, filter);
    }

    private List<Long> polysBetween(FindNearestPolyResult start, FindNearestPolyResult end, QueryFilter filter) {
        Result<List<Long>> found = query.findPath(start.getNearestRef(), end.getNearestRef(),
                start.getNearestPos(), end.getNearestPos(), filter);
        if (found.failed() || found.result == null || found.result.isEmpty()) return null;
        return found.result;
    }

    private List<StraightPathItem> corners(FindNearestPolyResult start, FindNearestPolyResult end, List<Long> polys, int options) {
        Result<List<StraightPathItem>> found = query.findStraightPath(start.getNearestPos(), end.getNearestPos(),
                polys, MAX_CORNERS, options);
        return found.failed() ? null : found.result;
    }

    private List<Waypoint> waypoints(List<StraightPathItem> corners, double spacing, QueryFilter filter) {
        List<Waypoint> waypoints = new ArrayList<>(corners.size());
        StraightPathItem first = corners.getFirst();
        waypoints.add(new Waypoint(vector(first.getPos()), Waypoint.Action.WALK, labels[areaOf(first.getRef())]));
        for (int i = 1; i < corners.size(); i++) {
            StraightPathItem before = corners.get(i - 1);
            Vector3 at = vector(corners.get(i).getPos());
            String label = labels[areaOf(before.getRef())];
            if (crossesLink(before)) {
                waypoints.add(new Waypoint(at, action(kindOf(before.getRef())), label));
                continue;
            }
            if (spacing > 0) fillIn(waypoints, at, spacing, label, filter);
            waypoints.add(new Waypoint(at, Waypoint.Action.WALK, label));
        }
        return waypoints;
    }

    private void fillIn(List<Waypoint> waypoints, Vector3 to, double spacing, String label, QueryFilter filter) {
        Vector3 from = waypoints.getLast().position();
        Vector3 across = to.sub(from);
        int pieces = (int) Math.ceil(across.length() / spacing - SPACING_SLACK);
        for (int piece = 1; piece < pieces; piece++) {
            Vector3 between = from.add(across.mul((double) piece / pieces));
            FindNearestPolyResult ground = nearest(between, EXTENTS, filter);
            if (ground != null) between = new Vector3(between.x(), ground.getNearestPos()[1], between.z());
            waypoints.add(new Waypoint(between, Waypoint.Action.WALK, label));
        }
    }

    private static boolean crossesLink(StraightPathItem corner) {
        return (corner.getFlags() & NavMeshQuery.DT_STRAIGHTPATH_OFFMESH_CONNECTION) != 0;
    }

    private static Waypoint.Action action(int kind) {
        return switch (kind) {
            case Links.JUMP -> Waypoint.Action.JUMP;
            case Links.CLIMB -> Waypoint.Action.CLIMB;
            case Links.CUSTOM -> Waypoint.Action.CUSTOM;
            default -> Waypoint.Action.WALK;
        };
    }

    private int areaOf(long ref) {
        if (ref == 0) return GROUND_AREA;
        Result<Tupple2<MeshTile, Poly>> found = mesh.getTileAndPolyByRef(ref);
        return found.failed() || found.result == null ? GROUND_AREA : found.result.second.getArea();
    }

    private int kindOf(long ref) {
        Result<Tupple2<MeshTile, Poly>> found = mesh.getTileAndPolyByRef(ref);
        if (found.failed() || found.result == null) return -1;
        Poly poly = found.result.second;
        if (poly.getType() != Poly.DT_POLYTYPE_OFFMESH_CONNECTION) return -1;
        MeshData data = found.result.first.data;
        int link = poly.index - data.header.offMeshBase;
        if (link < 0 || link >= data.offMeshCons.length) return -1;
        return data.offMeshCons[link].userId;
    }

    public boolean clear(World world, Vector3 from, Vector3 to) {
        return blocked(world, from, List.of(to), Agent.PLAYER) < 0;
    }

    public int blocked(World world, Vector3 from, List<Vector3> ahead) {
        return blocked(world, from, ahead, Agent.PLAYER);
    }

    public synchronized int blocked(World world, Vector3 from, List<Vector3> ahead, Agent agent) {
        Set<Key> wanted = new HashSet<>();
        Vector3 at = from;
        for (Vector3 point : ahead) {
            wanted.addAll(tilesAlong(at, point, 1));
            at = point;
        }
        if (wanted.size() > MAX_TILES) return -1;
        double lowest = from.y();
        double highest = from.y();
        for (Vector3 point : ahead) {
            lowest = Math.min(lowest, point.y());
            highest = Math.max(highest, point.y());
        }
        prepare(world, wanted, new Vector3(from.x(), lowest, from.z()), new Vector3(from.x(), highest, from.z()));
        QueryFilter filter = costs(agent);
        at = from;
        for (int i = 0; i < ahead.size(); i++) {
            if (!open(at, ahead.get(i), filter)) return i;
            at = ahead.get(i);
        }
        return -1;
    }

    private boolean open(Vector3 from, Vector3 to, QueryFilter filter) {
        FindNearestPolyResult start = nearest(from, EXTENTS, filter);
        FindNearestPolyResult end = nearest(to, EXTENTS, filter);
        if (start == null || end == null) return false;
        float[] snapped = end.getNearestPos();
        if (Math.hypot(snapped[0] - to.x(), snapped[2] - to.z()) > SNAP_REACH) return false;
        if (straightLine(start, end, filter)) return true;
        List<Long> polys = polysBetween(start, end, filter);
        if (polys == null || polys.getLast() != end.getNearestRef()) return false;
        List<StraightPathItem> corners = corners(start, end, polys, 0);
        if (corners == null) return false;
        double direct = Math.hypot(to.x() - from.x(), to.z() - from.z());
        return flatLength(corners) <= direct * DETOUR + DETOUR_EXTRA;
    }

    private boolean straightLine(FindNearestPolyResult start, FindNearestPolyResult end, QueryFilter filter) {
        Result<RaycastHit> ray = query.raycast(start.getNearestRef(), start.getNearestPos(), end.getNearestPos(), filter, 0, 0);
        return ray.succeeded() && ray.result != null && ray.result.t >= 1;
    }

    private static double flatLength(List<StraightPathItem> corners) {
        double length = 0;
        for (int i = 1; i < corners.size(); i++) {
            float[] before = corners.get(i - 1).getPos();
            float[] after = corners.get(i).getPos();
            length += Math.hypot(after[0] - before[0], after[2] - before[2]);
        }
        return length;
    }

    private static boolean reached(Vector3 last, Vector3 to) {
        double across = new Vector3(last.x() - to.x(), 0, last.z() - to.z()).length();
        return across < ARRIVE_ACROSS && Math.abs(last.y() - to.y()) < ARRIVE_HEIGHT;
    }

    private static Set<Key> tilesAlong(Vector3 a, Vector3 b, int reach) {
        Set<Key> keys = new HashSet<>();
        double length = Math.hypot(b.x() - a.x(), b.z() - a.z());
        int steps = Math.max(1, (int) Math.ceil(length / (TILE / 2)));
        for (int step = 0; step <= steps; step++) {
            double t = (double) step / steps;
            int tileX = tile(a.x() + (b.x() - a.x()) * t);
            int tileZ = tile(a.z() + (b.z() - a.z()) * t);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) keys.add(new Key(tileX + dx, tileZ + dz));
            }
            if (keys.size() > MAX_TILES) return keys;
        }
        return keys;
    }

    private static boolean covers(Vector3 a, Vector3 b, int reach) {
        int firstX = tile(Math.min(a.x(), b.x())) - CORRIDOR_MARGIN;
        int lastX = tile(Math.max(a.x(), b.x())) + CORRIDOR_MARGIN;
        int firstZ = tile(Math.min(a.z(), b.z())) - CORRIDOR_MARGIN;
        int lastZ = tile(Math.max(a.z(), b.z())) + CORRIDOR_MARGIN;
        return reach >= Math.max(lastX - firstX, lastZ - firstZ);
    }

    public synchronized Vector3 randomNear(World world, Vector3 centre, double range, Random random) {
        Set<Key> wanted = tilesAlong(centre, centre, (int) Math.ceil(range / TILE));
        if (wanted.size() > MAX_TILES) return null;
        prepare(world, wanted, centre, centre);
        FindNearestPolyResult start = nearest(centre, EXTENTS, walkable);
        if (start == null) return null;
        Result<FindRandomPointResult> found = query.findRandomPointAroundCircle(start.getNearestRef(),
                start.getNearestPos(), (float) range, walkable, new NavMeshQuery.FRand(random.nextLong()));
        if (found.failed() || found.result == null) return null;
        return vector(found.result.getRandomPt());
    }

    public synchronized int builtTiles() {
        int n = 0;
        for (Tile t : tiles.values()) if (t.built) n++;
        return n;
    }

    private FindNearestPolyResult nearest(Vector3 at, float[] extents, QueryFilter filter) {
        Result<FindNearestPolyResult> found = query.findNearestPoly(new float[] {(float) at.x(), (float) at.y(), (float) at.z()},
                extents, filter);
        if (found.failed() || found.result == null || found.result.getNearestRef() == 0) return null;
        return found.result;
    }

    private static Vector3 vector(float[] point) {
        return new Vector3(point[0], point[1], point[2]);
    }

    private void prepare(World world, Set<Key> wanted, Vector3 a, Vector3 b) {
        int minY = (int) Math.floor(Math.min(a.y(), b.y())) - REACH_Y;
        int maxY = (int) Math.ceil(Math.max(a.y(), b.y())) + REACH_Y;
        for (Key key : wanted) {
            Tile tile = tiles.computeIfAbsent(key, k -> new Tile());
            boolean taller = !tile.built || minY < tile.minY || maxY > tile.maxY;
            if (!taller && upToDate(world, key, tile)) continue;
            if (taller) tile.stretch(minY, maxY);
            build(world, key.x(), key.z(), tile);
        }
    }

    private boolean upToDate(World world, Key key, Tile tile) {
        return !tile.dirty && signature(world, key.x(), key.z(), tile.minY, tile.maxY) == tile.parts;
    }

    private static int tile(double x) {
        return (int) Math.floor(x / TILE);
    }

    private Bounds bounds(int tileX, int tileZ, int minY, int maxY) {
        float border = config.borderSize * CELL;
        Vector3 min = new Vector3(Math.floor(tileX * TILE - border) - 1, minY - 1, Math.floor(tileZ * TILE - border) - 1);
        Vector3 max = new Vector3(Math.ceil((tileX + 1) * TILE + border) + 1, maxY + 1, Math.ceil((tileZ + 1) * TILE + border) + 1);
        return new Bounds(min, max);
    }

    private void build(World world, int tileX, int tileZ, Tile tile) {
        float[] low = {0, tile.minY - 1, 0};
        float[] high = {0, tile.maxY + 2, 0};
        RecastBuilderConfig tileConfig = new RecastBuilderConfig(config, low, high, tileX, tileZ);
        Heightfield field = new Heightfield(tileConfig.width, tileConfig.height, tileConfig.bmin, tileConfig.bmax,
                CELL, CELL_HEIGHT, config.borderSize);
        int x0 = (int) Math.floor(tileConfig.bmin[0]);
        int z0 = (int) Math.floor(tileConfig.bmin[2]);
        int x1 = (int) Math.ceil(tileConfig.bmax[0]);
        int z1 = (int) Math.ceil(tileConfig.bmax[2]);
        int layers = tile.maxY - tile.minY + 3;
        Voxels voxels = new Voxels(world, x0, z0, x1, z1, tile.minY - 1, layers + 2, height);
        rasterizeBlocks(field, tileConfig, voxels, layers);

        Links links = new Links(voxels, tileX * TILE_BLOCKS, tileZ * TILE_BLOCKS, TILE_BLOCKS, climb);
        links.drops(MAX_DROP, GROUND_AREA);
        if (shape.canJump()) links.jumps(JUMP_AREA);
        links.climbs(CLIMB_AREA);

        Bounds around = bounds(tileX, tileZ, tile.minY, tile.maxY);
        Geometry geometry = new Geometry();
        List<ConvexVolume> volumes = new ArrayList<>();
        long parts = 0;
        for (World.Box box : world.boxes(around.min(), around.max())) {
            if (box.solid()) geometry.box(box.frame(), box.size());
            if (box.label() != null) volumes.add(volume(box, register(box.label())));
            parts += mix(box);
        }
        for (World.Link link : world.links(around.min(), around.max())) {
            parts += mix(link);
            boolean startsHere = tile(link.from().x()) == tileX && tile(link.from().z()) == tileZ;
            if (startsHere) links.add(link.from(), link.to(), register(link.label()), link.both(), Links.CUSTOM);
        }
        Telemetry telemetry = new Telemetry();
        rasterizeParts(field, geometry, telemetry);

        long ref = mesh.getTileRefAt(tileX, tileZ, 0);
        if (ref != 0) mesh.removeTile(ref);
        tile.parts = parts;
        tile.dirty = false;
        tile.built = true;

        RecastBuilder.RecastBuilderResult built = new RecastBuilder().build(tileX, tileZ, () -> volumes, config, field, telemetry);
        MeshData data = meshData(built, links, tileX, tileZ);
        if (data != null) mesh.addTile(data, 0, 0);
    }

    private void rasterizeBlocks(Heightfield field, RecastBuilderConfig tileConfig, Voxels voxels, int layers) {
        int shiftX = Math.round((tileConfig.bmin[0] - voxels.x0) / CELL);
        int shiftZ = Math.round((tileConfig.bmin[2] - voxels.z0) / CELL);
        for (int x = voxels.x0; x < voxels.x0 + voxels.width; x++) {
            for (int z = voxels.z0; z < voxels.z0 + voxels.depth; z++) {
                int firstColumn = (x - voxels.x0) * CELLS_PER_BLOCK - shiftX;
                int firstRow = (z - voxels.z0) * CELLS_PER_BLOCK - shiftZ;
                for (int layer = 0; layer < layers; layer++) {
                    BlockShape block = voxels.at(x, layer, z);
                    for (int box = 0; box < block.boxCount(); box++) {
                        int area = surfaceArea(block, voxels.onTop(x, layer, z, block.maxY(box)));
                        int bottom = Math.max(0, (int) Math.floor((layer + block.minY(box)) * CELLS_PER_LAYER + LAYER_SNAP));
                        int top = (int) Math.ceil((layer + block.maxY(box)) * CELLS_PER_LAYER - LAYER_SNAP);
                        if (top <= bottom) top = bottom + 1;
                        addSpans(field, block, box, firstColumn, firstRow, bottom, top, area);
                    }
                }
            }
        }
    }

    private int surfaceArea(BlockShape block, BlockShape onTop) {
        return switch (onTop.liquid()) {
            case LAVA -> LAVA_AREA;
            case WATER -> WATER_AREA;
            case NONE -> area(block.name());
        };
    }

    private void addSpans(Heightfield field, BlockShape block, int box, int firstColumn, int firstRow, int bottom, int top, int area) {
        for (int cellX = 0; cellX < CELLS_PER_BLOCK; cellX++) {
            if (!cellInside(cellX, block.minX(box), block.maxX(box))) continue;
            int column = firstColumn + cellX;
            if (column < 0 || column >= field.width) continue;
            for (int cellZ = 0; cellZ < CELLS_PER_BLOCK; cellZ++) {
                if (!cellInside(cellZ, block.minZ(box), block.maxZ(box))) continue;
                int row = firstRow + cellZ;
                if (row < 0 || row >= field.height) continue;
                RecastRasterization.addSpan(field, column, row, bottom, top, area, config.walkableClimb);
            }
        }
    }

    private static boolean cellInside(int cell, float min, float max) {
        float middle = (cell + 0.5f) * CELL;
        return middle >= min - CELL_SNAP && middle <= max + CELL_SNAP;
    }

    private void rasterizeParts(Heightfield field, Geometry geometry, Telemetry telemetry) {
        int triangles = geometry.triangles();
        if (triangles == 0) return;
        float[] vertices = geometry.vertices();
        int[] indices = geometry.indices();
        int[] marked = Recast.markWalkableTriangles(telemetry, SLOPE, vertices, indices, triangles, WALKABLE);
        RecastRasterization.rasterizeTriangles(field, vertices, indices, marked, triangles, config.walkableClimb, telemetry);
    }

    private MeshData meshData(RecastBuilder.RecastBuilderResult built, Links links, int tileX, int tileZ) {
        PolyMesh polys = built.getMesh();
        if (polys == null || polys.npolys == 0) return null;
        for (int i = 0; i < polys.npolys; i++) polys.flags[i] = polys.areas[i] == 0 ? 0 : 1;
        NavMeshDataCreateParams params = new NavMeshDataCreateParams();
        params.verts = polys.verts;
        params.vertCount = polys.nverts;
        params.polys = polys.polys;
        params.polyAreas = polys.areas;
        params.polyFlags = polys.flags;
        params.polyCount = polys.npolys;
        params.nvp = polys.nvp;
        if (links.count() > 0) {
            params.offMeshConCount = links.count();
            params.offMeshConVerts = links.verts();
            params.offMeshConAreas = links.areas();
            params.offMeshConDir = links.dirs();
            params.offMeshConUserID = links.kinds();
            params.offMeshConRad = new float[links.count()];
            Arrays.fill(params.offMeshConRad, radius + LINK_SLACK);
            params.offMeshConFlags = new int[links.count()];
            Arrays.fill(params.offMeshConFlags, 1);
        }
        PolyMeshDetail detail = built.getMeshDetail();
        if (detail != null) {
            params.detailMeshes = detail.meshes;
            params.detailVerts = detail.verts;
            params.detailVertsCount = detail.nverts;
            params.detailTris = detail.tris;
            params.detailTriCount = detail.ntris;
        }
        params.walkableHeight = height;
        params.walkableRadius = radius;
        params.walkableClimb = climb;
        params.bmin = polys.bmin;
        params.bmax = polys.bmax;
        params.cs = CELL;
        params.ch = CELL_HEIGHT;
        params.buildBvTree = true;
        params.tileX = tileX;
        params.tileZ = tileZ;
        return NavMeshBuilder.createNavMeshData(params);
    }

    private static Vector3[] corners(CFrame frame, Vector3 size) {
        Vector3 half = size.mul(0.5);
        Vector3[] corners = new Vector3[8];
        int corner = 0;
        for (double z : new double[] {-half.z(), half.z()}) {
            for (double y : new double[] {-half.y(), half.y()}) {
                for (double x : new double[] {-half.x(), half.x()}) {
                    corners[corner++] = frame.pointToWorld(new Vector3(x, y, z));
                }
            }
        }
        return corners;
    }

    private static ConvexVolume volume(World.Box box, int area) {
        Vector3[] corners = corners(box.frame(), box.size());
        float[] xs = new float[corners.length];
        float[] zs = new float[corners.length];
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        for (int i = 0; i < corners.length; i++) {
            xs[i] = (float) corners[i].x();
            zs[i] = (float) corners[i].z();
            lowest = Math.min(lowest, corners[i].y());
            highest = Math.max(highest, corners[i].y());
        }
        ConvexVolume volume = new ConvexVolume();
        volume.verts = hull(xs, zs);
        volume.hmin = (float) lowest - VOLUME_PAD;
        volume.hmax = (float) highest + VOLUME_PAD;
        volume.areaMod = new AreaModification(area);
        return volume;
    }

    private static float[] hull(float[] xs, float[] zs) {
        int count = xs.length;
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> xs[a] != xs[b] ? Float.compare(xs[a], xs[b]) : Float.compare(zs[a], zs[b]));

        int[] chain = new int[2 * count];
        int size = 0;
        for (int i = 0; i < count; i++) size = pushTurningLeft(chain, size, 0, xs, zs, order[i]);
        size--;
        int upperStart = size;
        for (int i = count - 1; i >= 0; i--) size = pushTurningLeft(chain, size, upperStart, xs, zs, order[i]);
        size--;

        float[] verts = new float[size * 3];
        for (int i = 0; i < size; i++) {
            verts[i * 3] = xs[chain[i]];
            verts[i * 3 + 2] = zs[chain[i]];
        }
        return verts;
    }

    private static int pushTurningLeft(int[] chain, int size, int start, float[] xs, float[] zs, int point) {
        while (size >= start + 2 && cross(xs, zs, chain[size - 2], chain[size - 1], point) <= 0) size--;
        chain[size] = point;
        return size + 1;
    }

    private static float cross(float[] xs, float[] zs, int origin, int a, int b) {
        return (xs[a] - xs[origin]) * (zs[b] - zs[origin]) - (zs[a] - zs[origin]) * (xs[b] - xs[origin]);
    }

    private long signature(World world, int tileX, int tileZ, int minY, int maxY) {
        Bounds around = bounds(tileX, tileZ, minY, maxY);
        long sum = 0;
        for (World.Box box : world.boxes(around.min(), around.max())) sum += mix(box);
        for (World.Link link : world.links(around.min(), around.max())) sum += mix(link);
        return sum;
    }

    private static long mix(World.Box box) {
        long hash = box.frame().position().hashCode() * 31L + box.frame().rotation().hashCode();
        hash = hash * 31L + box.size().hashCode();
        hash = hash * 31L + Objects.hashCode(box.label()) * 2L + (box.solid() ? 1 : 0);
        return hash * BOX_SPREAD;
    }

    private static long mix(World.Link link) {
        long hash = link.from().hashCode() * 31L + link.to().hashCode();
        hash = hash * 31L + Objects.hashCode(link.label()) * 2L + (link.both() ? 1 : 0);
        return (hash + 7) * LINK_SPREAD;
    }

    private static final class Geometry {
        private static final int[][] FACES = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

        private float[] v = new float[3 * 4096];
        private int[] f = new int[3 * 4096];
        private int vn;
        private int fn;

        int triangles() {
            return fn / 3;
        }

        float[] vertices() {
            return Arrays.copyOf(v, vn);
        }

        int[] indices() {
            return Arrays.copyOf(f, fn);
        }

        void box(CFrame frame, Vector3 size) {
            Vector3 centre = frame.position();
            Vector3[] corners = corners(frame, size);
            int[] vertices = new int[corners.length];
            for (int i = 0; i < corners.length; i++) vertices[i] = vertex(corners[i].x(), corners[i].y(), corners[i].z());
            for (int[] face : FACES) {
                outward(centre, corners, vertices, face[0], face[1], face[2]);
                outward(centre, corners, vertices, face[0], face[2], face[3]);
            }
        }

        private void outward(Vector3 centre, Vector3[] corners, int[] vertices, int i, int j, int k) {
            Vector3 normal = corners[j].sub(corners[i]).cross(corners[k].sub(corners[i]));
            Vector3 middle = corners[i].add(corners[j]).add(corners[k]).mul(1.0 / 3);
            if (normal.dot(middle.sub(centre)) >= 0) {
                face(vertices[i], vertices[j], vertices[k]);
            } else {
                face(vertices[i], vertices[k], vertices[j]);
            }
        }

        private int vertex(double x, double y, double z) {
            if (vn + 3 > v.length) v = Arrays.copyOf(v, v.length * 2);
            v[vn++] = (float) x;
            v[vn++] = (float) y;
            v[vn++] = (float) z;
            return vn / 3 - 1;
        }

        private void face(int a, int b, int c) {
            if (fn + 3 > f.length) f = Arrays.copyOf(f, f.length * 2);
            f[fn++] = a;
            f[fn++] = b;
            f[fn++] = c;
        }
    }
}
