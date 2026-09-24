package com.meekdev.moud.core.nav;

import org.recast4j.detour.MeshTile;
import org.recast4j.detour.NavMesh;
import org.recast4j.detour.Poly;
import org.recast4j.detour.QueryFilter;

final class Costs implements QueryFilter {

    private final float[] costs = new float[NavMesh.DT_MAX_AREAS];

    Costs(float[] costs) {
        System.arraycopy(costs, 0, this.costs, 0, Math.min(costs.length, this.costs.length));
    }

    @Override
    public boolean passFilter(long ref, MeshTile tile, Poly poly) {
        return poly.flags != 0 && Float.isFinite(costs[poly.getArea()]);
    }

    @Override
    public float getCost(float[] from, float[] to, long prevRef, MeshTile prevTile, Poly prevPoly, long curRef,
            MeshTile curTile, Poly curPoly, long nextRef, MeshTile nextTile, Poly nextPoly) {
        float dx = to[0] - from[0];
        float dy = to[1] - from[1];
        float dz = to[2] - from[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * costs[curPoly.getArea()];
    }
}
