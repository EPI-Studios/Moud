package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.recast4j.detour.DefaultQueryFilter;
import org.recast4j.detour.FindNearestPolyResult;
import org.recast4j.detour.FindRandomPointResult;
import org.recast4j.detour.MeshData;
import org.recast4j.detour.NavMesh;
import org.recast4j.detour.NavMeshBuilder;
import org.recast4j.detour.NavMeshDataCreateParams;
import org.recast4j.detour.NavMeshParams;
import org.recast4j.detour.NavMeshQuery;
import org.recast4j.detour.QueryFilter;
import org.recast4j.detour.Result;
import org.recast4j.detour.StraightPathItem;
import org.recast4j.recast.AreaModification;
import org.recast4j.recast.PolyMesh;
import org.recast4j.recast.PolyMeshDetail;
import org.recast4j.recast.RecastBuilder;
import org.recast4j.recast.RecastBuilderConfig;
import org.recast4j.recast.RecastConfig;
import org.recast4j.recast.RecastConstants;
import org.recast4j.recast.geom.SimpleInputGeomProvider;

public final class NavMeshes {

    public interface World {
        boolean solid(int x, int y, int z);

        record Box(CFrame frame, Vector3 size) {}

        List<Box> boxes(Vector3 min, Vector3 max);
    }

    private static final float RADIUS = 0.3f;
    private static final float HEIGHT = 1.8f;
    private static final float CLIMB = 1.05f;
    private static final float SLOPE = 50;
    private static final float CELL = 0.25f;
    private static final float CELL_HEIGHT = 0.1f;
    private static final int TILE_CELLS = 128;
    private static final float TILE = TILE_CELLS * CELL;
    private static final int VERTS_PER_POLY = 6;
    private static final int REACH_Y = 16;
    private static final int BUILD_BUDGET = 6;
    private static final int MAX_TILES = 16;
    private static final float[] EXTENTS = {1.5f, 3f, 1.5f};

    private static final AreaModification WALKABLE = new AreaModification(1);
    private static final RecastConfig CONFIG = new RecastConfig(true, TILE_CELLS, TILE_CELLS,
            RecastConfig.calcBorder(RADIUS, CELL), RecastConstants.PartitionType.WATERSHED,
            CELL, CELL_HEIGHT, SLOPE, true, true, true, HEIGHT, RADIUS, CLIMB,
            8 * 8 * CELL * CELL, 20 * 20 * CELL * CELL, 12, 1.3f, VERTS_PER_POLY, true, 6, 1, WALKABLE);

    private record Key(int x, int z) {}

    private static final class Tile {
        int minY;
        int maxY;
        long parts;
        boolean dirty;
        boolean built;
    }

    private final NavMesh mesh;
    private final NavMeshQuery query;
    private final QueryFilter filter = new DefaultQueryFilter();
    private final Map<Key, Tile> tiles = new HashMap<>();

    public NavMeshes() {
        NavMeshParams params = new NavMeshParams();
        params.tileWidth = TILE;
        params.tileHeight = TILE;
        params.maxTiles = 1 << 14;
        params.maxPolys = 1 << 14;
        mesh = new NavMesh(params, VERTS_PER_POLY);
        query = new NavMeshQuery(mesh);
    }

    public void blockChanged(int x, int y, int z) {
        float border = CONFIG.borderSize * CELL + 1;
        for (int tx = tile(x - border); tx <= tile(x + border); tx++) {
            for (int tz = tile(z - border); tz <= tile(z + border); tz++) {
                Tile t = tiles.get(new Key(tx, tz));
                if (t != null) t.dirty = true;
            }
        }
    }

    public List<Vector3> find(World world, Vector3 from, Vector3 to, boolean partial) {
        if (!prepare(world, from, to)) return null;
        FindNearestPolyResult start = nearest(from);
        FindNearestPolyResult end = nearest(to);
        if (start == null) return null;
        if (end == null) {
            if (!partial) return null;
            end = nearest(from.add(to.sub(from).normalize().mul(Math.min(to.sub(from).length(), TILE))));
            if (end == null) return null;
        }
        Result<List<Long>> corridor = query.findPath(start.getNearestRef(), end.getNearestRef(),
                start.getNearestPos(), end.getNearestPos(), filter);
        if (corridor.failed() || corridor.result == null || corridor.result.isEmpty()) return null;
        Result<List<StraightPathItem>> straight = query.findStraightPath(start.getNearestPos(), end.getNearestPos(),
                corridor.result, 256, 0);
        if (straight.failed() || straight.result == null || straight.result.isEmpty()) return null;
        List<Vector3> out = new ArrayList<>(straight.result.size());
        for (StraightPathItem item : straight.result) {
            float[] p = item.getPos();
            out.add(new Vector3(p[0], p[1], p[2]));
        }
        Vector3 last = out.getLast();
        boolean reached = new Vector3(last.x() - to.x(), 0, last.z() - to.z()).length() < 1.0 && Math.abs(last.y() - to.y()) < 2.5;
        if (!reached && !partial) return null;
        if (out.size() > 1) out.removeFirst();
        return out;
    }

    public Vector3 randomNear(World world, Vector3 centre, double radius, Random random) {
        if (!prepare(world, centre.sub(new Vector3(radius, 0, radius)), centre.add(new Vector3(radius, 0, radius)))) return null;
        FindNearestPolyResult start = nearest(centre);
        if (start == null) return null;
        Result<FindRandomPointResult> found = query.findRandomPointAroundCircle(start.getNearestRef(),
                start.getNearestPos(), (float) radius, filter, new NavMeshQuery.FRand(random.nextLong()));
        if (found.failed() || found.result == null) return null;
        float[] p = found.result.getRandomPt();
        return new Vector3(p[0], p[1], p[2]);
    }

    public int builtTiles() {
        int n = 0;
        for (Tile t : tiles.values()) if (t.built) n++;
        return n;
    }

    private FindNearestPolyResult nearest(Vector3 at) {
        Result<FindNearestPolyResult> found = query.findNearestPoly(new float[] {(float) at.x(), (float) at.y(), (float) at.z()},
                EXTENTS, filter);
        if (found.failed() || found.result == null || found.result.getNearestRef() == 0) return null;
        return found.result;
    }

    private boolean prepare(World world, Vector3 a, Vector3 b) {
        int minY = (int) Math.floor(Math.min(a.y(), b.y())) - REACH_Y;
        int maxY = (int) Math.ceil(Math.max(a.y(), b.y())) + REACH_Y;
        int x0 = tile(Math.min(a.x(), b.x()) - 4), x1 = tile(Math.max(a.x(), b.x()) + 4);
        int z0 = tile(Math.min(a.z(), b.z()) - 4), z1 = tile(Math.max(a.z(), b.z()) + 4);
        if ((x1 - x0 + 1) * (z1 - z0 + 1) > MAX_TILES * MAX_TILES) return false;
        int budget = BUILD_BUDGET;
        for (int tx = x0; tx <= x1; tx++) {
            for (int tz = z0; tz <= z1; tz++) {
                Key key = new Key(tx, tz);
                Tile t = tiles.computeIfAbsent(key, k -> new Tile());
                boolean taller = !t.built || minY < t.minY || maxY > t.maxY;
                long parts = t.built && !taller && !t.dirty ? signature(world, tx, tz, t.minY, t.maxY) : 0;
                if (t.built && !taller && !t.dirty && parts == t.parts) continue;
                if (budget-- <= 0) continue;
                if (taller) {
                    t.minY = t.built ? Math.min(t.minY, minY) : minY;
                    t.maxY = t.built ? Math.max(t.maxY, maxY) : maxY;
                }
                build(world, tx, tz, t);
            }
        }
        return true;
    }

    private static int tile(double x) {
        return (int) Math.floor(x / TILE);
    }

    private void build(World world, int tx, int tz, Tile t) {
        float border = CONFIG.borderSize * CELL;
        int x0 = (int) Math.floor(tx * TILE - border) - 1, x1 = (int) Math.ceil((tx + 1) * TILE + border) + 1;
        int z0 = (int) Math.floor(tz * TILE - border) - 1, z1 = (int) Math.ceil((tz + 1) * TILE + border) + 1;
        Geometry geometry = new Geometry();

        int height = t.maxY - t.minY + 3;
        boolean[] column = new boolean[height];
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                for (int i = 0; i < height; i++) column[i] = world.solid(x, t.minY - 1 + i, z);
                for (int i = 1; i < height - 1; i++) {
                    if (!column[i]) continue;
                    int y = t.minY - 1 + i;
                    if (!column[i + 1]) geometry.flat(x, y + 1, z, true);
                    if (!column[i - 1]) geometry.flat(x, y, z, false);
                }
            }
        }
        long parts = 0;
        for (World.Box box : world.boxes(new Vector3(x0, t.minY - 1, z0), new Vector3(x1, t.maxY + 1, z1))) {
            geometry.box(box.frame(), box.size());
            parts += mix(box.frame(), box.size());
        }

        long ref = mesh.getTileRefAt(tx, tz, 0);
        if (ref != 0) mesh.removeTile(ref);
        t.parts = parts;
        t.dirty = false;
        t.built = true;
        if (geometry.triangles() == 0) return;

        SimpleInputGeomProvider input = new SimpleInputGeomProvider(geometry.vertices(), geometry.indices());
        float[] bmin = {0, t.minY - 1, 0};
        float[] bmax = {0, t.maxY + 2, 0};
        RecastBuilderConfig config = new RecastBuilderConfig(CONFIG, bmin, bmax, tx, tz);
        RecastBuilder.RecastBuilderResult built = new RecastBuilder().build(input, config);
        PolyMesh polys = built.getMesh();
        if (polys == null || polys.npolys == 0) return;
        for (int i = 0; i < polys.npolys; i++) polys.flags[i] = polys.areas[i] == 0 ? 0 : 1;
        NavMeshDataCreateParams params = new NavMeshDataCreateParams();
        params.verts = polys.verts;
        params.vertCount = polys.nverts;
        params.polys = polys.polys;
        params.polyAreas = polys.areas;
        params.polyFlags = polys.flags;
        params.polyCount = polys.npolys;
        params.nvp = polys.nvp;
        PolyMeshDetail detail = built.getMeshDetail();
        if (detail != null) {
            params.detailMeshes = detail.meshes;
            params.detailVerts = detail.verts;
            params.detailVertsCount = detail.nverts;
            params.detailTris = detail.tris;
            params.detailTriCount = detail.ntris;
        }
        params.walkableHeight = HEIGHT;
        params.walkableRadius = RADIUS;
        params.walkableClimb = CLIMB;
        params.bmin = polys.bmin;
        params.bmax = polys.bmax;
        params.cs = CELL;
        params.ch = CELL_HEIGHT;
        params.buildBvTree = true;
        params.tileX = tx;
        params.tileZ = tz;
        MeshData data = NavMeshBuilder.createNavMeshData(params);
        if (data != null) mesh.addTile(data, 0, 0);
    }

    private static long signature(World world, int tx, int tz, int minY, int maxY) {
        float border = CONFIG.borderSize * CELL;
        long sum = 0;
        for (World.Box box : world.boxes(new Vector3(Math.floor(tx * TILE - border) - 1, minY - 1, Math.floor(tz * TILE - border) - 1),
                new Vector3(Math.ceil((tx + 1) * TILE + border) + 1, maxY + 1, Math.ceil((tz + 1) * TILE + border) + 1))) {
            sum += mix(box.frame(), box.size());
        }
        return sum;
    }

    private static long mix(CFrame frame, Vector3 size) {
        long h = frame.position().hashCode() * 31L + frame.rotation().hashCode();
        return (h * 31L + size.hashCode()) * 0x9E3779B97F4A7C15L;
    }

    private static final class Geometry {
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

        void flat(int x, int y, int z, boolean up) {
            int a = vertex(x, y, z), b = vertex(x, y, z + 1), c = vertex(x + 1, y, z + 1), d = vertex(x + 1, y, z);
            if (up) {
                face(a, b, c);
                face(a, c, d);
            } else {
                face(a, c, b);
                face(a, d, c);
            }
        }

        void box(CFrame frame, Vector3 size) {
            Vector3 half = size.mul(0.5);
            Vector3 centre = frame.position();
            int[] corner = new int[8];
            Vector3[] at = new Vector3[8];
            for (int i = 0; i < 8; i++) {
                Vector3 local = new Vector3((i & 1) == 0 ? -half.x() : half.x(), (i & 2) == 0 ? -half.y() : half.y(),
                        (i & 4) == 0 ? -half.z() : half.z());
                at[i] = frame.pointToWorld(local);
                corner[i] = vertex(at[i].x(), at[i].y(), at[i].z());
            }
            int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
            for (int[] q : faces) {
                outward(centre, at, corner, q[0], q[1], q[2]);
                outward(centre, at, corner, q[0], q[2], q[3]);
            }
        }

        private void outward(Vector3 centre, Vector3[] at, int[] corner, int i, int j, int k) {
            Vector3 normal = at[j].sub(at[i]).cross(at[k].sub(at[i]));
            Vector3 middle = at[i].add(at[j]).add(at[k]).mul(1.0 / 3);
            if (normal.dot(middle.sub(centre)) >= 0) {
                face(corner[i], corner[j], corner[k]);
            } else {
                face(corner[i], corner[k], corner[j]);
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
