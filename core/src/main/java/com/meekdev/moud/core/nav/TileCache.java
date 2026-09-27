package com.meekdev.moud.core.nav;

import static com.meekdev.moud.core.nav.TileGrid.tileIndex;

import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.nav.NavMeshes.World;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class TileCache {

    static final int VERTICAL_REACH_BLOCKS = 16;
    private static final int LOWEST_BUILD_Y = -128;
    private static final int HIGHEST_BUILD_Y = 448;

    record TileKey(int x, int z) {}

    private static final class CachedTile {
        int minY;
        int maxY;
        long fingerprint;
        boolean dirty;
        boolean built;

        void stretch(int low, int high) {
            minY = built ? Math.min(minY, low) : low;
            maxY = built ? Math.max(maxY, high) : high;
        }
    }

    private final Map<TileKey, CachedTile> tiles = new HashMap<>();
    private final TileBuilder builder;

    TileCache(TileBuilder builder) {
        this.builder = builder;
    }

    void markChangedAround(int x, int z) {
        float border = builder.border() + 1;
        for (int tileX = tileIndex(x - border); tileX <= tileIndex(x + border); tileX++) {
            for (int tileZ = tileIndex(z - border); tileZ <= tileIndex(z + border); tileZ++) {
                CachedTile cached = tiles.get(new TileKey(tileX, tileZ));
                if (cached != null) cached.dirty = true;
            }
        }
    }

    void markAllDirty() {
        for (CachedTile cached : tiles.values()) cached.dirty = true;
    }

    int builtCount() {
        int n = 0;
        for (CachedTile cached : tiles.values()) if (cached.built) n++;
        return n;
    }

    void prepare(World world, Set<TileKey> wanted, Vector3 a, Vector3 b) {
        int minY = (int) Math.floor(Math.clamp(Math.min(a.y(), b.y()), LOWEST_BUILD_Y, HIGHEST_BUILD_Y)) - VERTICAL_REACH_BLOCKS;
        int maxY = (int) Math.ceil(Math.clamp(Math.max(a.y(), b.y()), LOWEST_BUILD_Y, HIGHEST_BUILD_Y)) + VERTICAL_REACH_BLOCKS;
        for (TileKey key : wanted) {
            CachedTile cached = tiles.computeIfAbsent(key, k -> new CachedTile());
            boolean taller = !cached.built || minY < cached.minY || maxY > cached.maxY;
            if (!taller && upToDate(world, key, cached)) continue;
            if (taller) cached.stretch(minY, maxY);
            rebuild(world, key, cached);
        }
    }

    private boolean upToDate(World world, TileKey key, CachedTile cached) {
        if (cached.dirty) return false;
        TileBuilder.Bounds around = builder.bounds(key.x(), key.z(), cached.minY, cached.maxY);
        return Fingerprints.contentsWithin(world, around.min(), around.max()) == cached.fingerprint;
    }

    private void rebuild(World world, TileKey key, CachedTile cached) {
        TileBuilder.Rasterized rasterized = builder.rasterize(world, key.x(), key.z(), cached.minY, cached.maxY);
        builder.removeTile(key.x(), key.z());
        cached.dirty = true;
        builder.addTile(rasterized);
        cached.fingerprint = rasterized.fingerprint();
        cached.dirty = false;
        cached.built = true;
    }
}
