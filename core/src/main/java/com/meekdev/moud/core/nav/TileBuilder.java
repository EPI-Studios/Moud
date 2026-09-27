package com.meekdev.moud.core.nav;

import static com.meekdev.moud.core.nav.TileGrid.CELL;
import static com.meekdev.moud.core.nav.TileGrid.CELLS_PER_BLOCK;
import static com.meekdev.moud.core.nav.TileGrid.CELLS_PER_LAYER;
import static com.meekdev.moud.core.nav.TileGrid.CELL_HEIGHT;
import static com.meekdev.moud.core.nav.TileGrid.TILE_BLOCKS;
import static com.meekdev.moud.core.nav.TileGrid.TILE_CELLS;
import static com.meekdev.moud.core.nav.TileGrid.TILE_SIZE;
import static com.meekdev.moud.core.nav.TileGrid.tileIndex;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.NavMeshes.World;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.recast4j.detour.MeshData;
import org.recast4j.detour.NavMesh;
import org.recast4j.detour.NavMeshBuilder;
import org.recast4j.detour.NavMeshDataCreateParams;
import org.recast4j.detour.NavMeshParams;
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

final class TileBuilder {

    private static final float STEP_HEIGHT = 0.6f;
    private static final float JUMP_HEIGHT = 1.05f;
    private static final float MAX_SLOPE_DEGREES = 50;
    private static final float MAX_DROP_HEIGHT = 3.2f;
    private static final int VERTS_PER_POLY = 6;
    private static final double LAYER_ROUNDING_SLACK = 1e-3;
    private static final float CELL_EDGE_SLACK = 1e-3f;
    private static final float LINK_RADIUS_SLACK = 0.3f;
    private static final AreaModification WALKABLE = new AreaModification(AreaLabels.GROUND);

    record Bounds(Vector3 min, Vector3 max) {}

    record Rasterized(int tileX, int tileZ, Heightfield field, Links links, List<ConvexVolume> volumes,
            Telemetry telemetry, long fingerprint) {}

    private final Agent.Shape shape;
    private final float radius;
    private final float height;
    private final float climb;
    private final RecastConfig config;
    private final NavMesh mesh;
    private final AreaLabels areas;

    TileBuilder(Agent.Shape shape, AreaLabels areas) {
        this.shape = shape;
        this.areas = areas;
        radius = (float) shape.radius();
        height = (float) shape.height();
        climb = shape.canJump() ? JUMP_HEIGHT : STEP_HEIGHT;
        config = recastConfig(radius, height, climb);
        NavMeshParams params = new NavMeshParams();
        params.tileWidth = TILE_SIZE;
        params.tileHeight = TILE_SIZE;
        params.maxTiles = 1 << 14;
        params.maxPolys = 1 << 14;
        mesh = new NavMesh(params, VERTS_PER_POLY);
    }

    private static RecastConfig recastConfig(float radius, float height, float climb) {
        float erode = (float) (Math.floor(radius / CELL + 0.01) * CELL);
        float minRegion = 8 * 8 * CELL * CELL;
        float mergeRegion = 20 * 20 * CELL * CELL;
        return new RecastConfig(true, TILE_CELLS, TILE_CELLS, RecastConfig.calcBorder(radius, CELL),
                RecastConstants.PartitionType.WATERSHED, CELL, CELL_HEIGHT, MAX_SLOPE_DEGREES, true, true, true, height,
                erode, climb, minRegion, mergeRegion, 12, 1.3f, VERTS_PER_POLY, true, 6, 1, WALKABLE);
    }

    NavMesh mesh() {
        return mesh;
    }

    float border() {
        return config.borderSize * CELL;
    }

    Bounds bounds(int tileX, int tileZ, int minY, int maxY) {
        float border = border();
        Vector3 min = new Vector3(Math.floor(tileX * TILE_SIZE - border) - 1, minY - 1, Math.floor(tileZ * TILE_SIZE - border) - 1);
        Vector3 max = new Vector3(Math.ceil((tileX + 1) * TILE_SIZE + border) + 1, maxY + 1,
                Math.ceil((tileZ + 1) * TILE_SIZE + border) + 1);
        return new Bounds(min, max);
    }

    Rasterized rasterize(World world, int tileX, int tileZ, int minY, int maxY) {
        float[] low = {0, minY - 1, 0};
        float[] high = {0, maxY + 2, 0};
        RecastBuilderConfig tileConfig = new RecastBuilderConfig(config, low, high, tileX, tileZ);
        Heightfield field = new Heightfield(tileConfig.width, tileConfig.height, tileConfig.bmin, tileConfig.bmax,
                CELL, CELL_HEIGHT, config.borderSize);
        int x0 = (int) Math.floor(tileConfig.bmin[0]);
        int z0 = (int) Math.floor(tileConfig.bmin[2]);
        int x1 = (int) Math.ceil(tileConfig.bmax[0]);
        int z1 = (int) Math.ceil(tileConfig.bmax[2]);
        int layers = maxY - minY + 3;
        Voxels voxels = new Voxels(world, x0, z0, x1, z1, minY - 1, layers + 2, height);
        rasterizeBlocks(field, tileConfig, voxels, layers);

        Links links = blockLinks(voxels, tileX, tileZ);

        Bounds around = bounds(tileX, tileZ, minY, maxY);
        Geometry geometry = new Geometry();
        List<ConvexVolume> volumes = new ArrayList<>();
        long fingerprint = 0;
        for (World.Box box : world.boxes(around.min(), around.max())) {
            if (box.solid()) geometry.box(box.frame(), box.size());
            if (box.label() != null) volumes.add(Boxes.labelledVolume(box, areas.register(box.label())));
            fingerprint += Fingerprints.ofBox(box);
        }
        for (World.Link link : world.links(around.min(), around.max())) {
            fingerprint += Fingerprints.ofLink(link);
            boolean startsHere = tileIndex(link.from().x()) == tileX && tileIndex(link.from().z()) == tileZ;
            if (startsHere) links.add(link.from(), link.to(), areas.register(link.label()), link.both(), Links.CUSTOM);
        }
        Telemetry telemetry = new Telemetry();
        rasterizeParts(field, geometry, telemetry);
        return new Rasterized(tileX, tileZ, field, links, volumes, telemetry, fingerprint);
    }

    void removeTile(int tileX, int tileZ) {
        long ref = mesh.getTileRefAt(tileX, tileZ, 0);
        if (ref != 0) mesh.removeTile(ref);
    }

    void addTile(Rasterized rasterized) {
        int tileX = rasterized.tileX();
        int tileZ = rasterized.tileZ();
        RecastBuilder.RecastBuilderResult recast = new RecastBuilder().build(tileX, tileZ, rasterized::volumes, config,
                rasterized.field(), rasterized.telemetry());
        MeshData data = meshData(recast, rasterized.links(), tileX, tileZ);
        if (data != null) mesh.addTile(data, 0, 0);
    }

    private Links blockLinks(Voxels voxels, int tileX, int tileZ) {
        Links links = new Links(voxels, tileX * TILE_BLOCKS, tileZ * TILE_BLOCKS, TILE_BLOCKS, climb);
        links.drops(MAX_DROP_HEIGHT, AreaLabels.GROUND);
        if (shape.canJump()) links.jumps(AreaLabels.JUMP);
        links.climbs(AreaLabels.CLIMB);
        return links;
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
                        int bottom = Math.max(0, (int) Math.floor((layer + block.minY(box)) * CELLS_PER_LAYER + LAYER_ROUNDING_SLACK));
                        int top = (int) Math.ceil((layer + block.maxY(box)) * CELLS_PER_LAYER - LAYER_ROUNDING_SLACK);
                        if (top <= bottom) top = bottom + 1;
                        addSpans(field, block, box, firstColumn, firstRow, bottom, top, area);
                    }
                }
            }
        }
    }

    private int surfaceArea(BlockShape block, BlockShape onTop) {
        return switch (onTop.liquid()) {
            case LAVA -> AreaLabels.LAVA;
            case WATER -> AreaLabels.WATER;
            case NONE -> areas.known(block.name());
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
        return middle >= min - CELL_EDGE_SLACK && middle <= max + CELL_EDGE_SLACK;
    }

    private void rasterizeParts(Heightfield field, Geometry geometry, Telemetry telemetry) {
        int triangles = geometry.triangles();
        if (triangles == 0) return;
        float[] vertices = geometry.vertices();
        int[] indices = geometry.indices();
        int[] marked = Recast.markWalkableTriangles(telemetry, MAX_SLOPE_DEGREES, vertices, indices, triangles, WALKABLE);
        RecastRasterization.rasterizeTriangles(field, vertices, indices, marked, triangles, config.walkableClimb, telemetry);
    }

    private MeshData meshData(RecastBuilder.RecastBuilderResult recast, Links links, int tileX, int tileZ) {
        PolyMesh polys = recast.getMesh();
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
            Arrays.fill(params.offMeshConRad, radius + LINK_RADIUS_SLACK);
            params.offMeshConFlags = new int[links.count()];
            Arrays.fill(params.offMeshConFlags, 1);
        }
        PolyMeshDetail detail = recast.getMeshDetail();
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
}
