package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.recast4j.detour.FindNearestPolyResult;
import org.recast4j.detour.FindRandomPointResult;
import org.recast4j.detour.MeshData;
import org.recast4j.detour.MeshTile;
import org.recast4j.detour.NavMesh;
import org.recast4j.detour.NavMeshQuery;
import org.recast4j.detour.Poly;
import org.recast4j.detour.QueryFilter;
import org.recast4j.detour.RaycastHit;
import org.recast4j.detour.Result;
import org.recast4j.detour.StraightPathItem;
import org.recast4j.detour.Tupple2;

final class PathSearch {

    private static final float[] NEAR_EXTENTS = {1.5f, 3f, 1.5f};
    private static final float[] BELOW_TARGET_EXTENTS = {1.5f, TileCache.VERTICAL_REACH_BLOCKS, 1.5f};
    private static final int MAX_STRAIGHT_PATH_POINTS = 1024;
    private static final double SPACING_ROUNDING_SLACK = 1e-6;
    private static final double ARRIVED_WITHIN_METRES = 1.0;
    private static final double ARRIVED_WITHIN_HEIGHT = 2.5;
    private static final double MAX_END_SNAP_METRES = 0.6;
    private static final double DETOUR_RATIO = 1.05;
    private static final double DETOUR_EXTRA_METRES = 0.5;

    private final NavMesh mesh;
    private final NavMeshQuery query;
    private final AreaLabels areas;

    PathSearch(NavMesh mesh, AreaLabels areas) {
        this.mesh = mesh;
        this.areas = areas;
        query = new NavMeshQuery(mesh);
    }

    boolean onMesh(Vector3 at, QueryFilter filter) {
        return nearest(at, NEAR_EXTENTS, filter) != null;
    }

    Route search(Vector3 from, Vector3 to, QueryFilter filter, Agent agent) {
        FindNearestPolyResult start = nearest(from, NEAR_EXTENTS, filter);
        if (start == null) return Route.NO_PATH;
        FindNearestPolyResult end = goal(from, to, filter);
        if (end == null) return Route.NO_PATH;
        List<Long> polys = polysBetween(start, end, filter);
        if (polys == null) return Route.NO_PATH;
        List<StraightPathItem> straightPath = straightPath(start, end, polys, NavMeshQuery.DT_STRAIGHTPATH_AREA_CROSSINGS);
        if (straightPath == null || straightPath.isEmpty()) return Route.NO_PATH;
        List<Waypoint> waypoints = waypoints(straightPath, agent.spacing(), filter);
        Vector3 last = waypoints.getLast().position();
        return new Route(reached(last, to) ? Route.Status.SUCCESS : Route.Status.CLOSEST, waypoints);
    }

    boolean segmentOpen(Vector3 from, Vector3 to, QueryFilter filter) {
        FindNearestPolyResult start = nearest(from, NEAR_EXTENTS, filter);
        FindNearestPolyResult end = nearest(to, NEAR_EXTENTS, filter);
        if (start == null || end == null) return false;
        float[] snapped = end.getNearestPos();
        if (Math.hypot(snapped[0] - to.x(), snapped[2] - to.z()) > MAX_END_SNAP_METRES) return false;
        if (straightLine(start, end, filter)) return true;
        List<Long> polys = polysBetween(start, end, filter);
        if (polys == null || polys.getLast() != end.getNearestRef()) return false;
        List<StraightPathItem> straightPath = straightPath(start, end, polys, 0);
        if (straightPath == null) return false;
        double direct = Math.hypot(to.x() - from.x(), to.z() - from.z());
        return flatLength(straightPath) <= direct * DETOUR_RATIO + DETOUR_EXTRA_METRES;
    }

    Vector3 randomPointNear(Vector3 centre, double range, QueryFilter filter, Random random) {
        FindNearestPolyResult start = nearest(centre, NEAR_EXTENTS, filter);
        if (start == null) return null;
        Result<FindRandomPointResult> found = query.findRandomPointWithinCircle(start.getNearestRef(),
                start.getNearestPos(), (float) range, filter, new NavMeshQuery.FRand(random.nextLong()));
        if (found.failed() || found.result == null) return null;
        return vector(found.result.getRandomPt());
    }

    private FindNearestPolyResult goal(Vector3 from, Vector3 to, QueryFilter filter) {
        FindNearestPolyResult end = nearest(to, NEAR_EXTENTS, filter);
        if (end == null) end = nearest(to, BELOW_TARGET_EXTENTS, filter);
        if (end != null) return end;
        Vector3 toward = to.sub(from);
        Vector3 withinATile = from.add(toward.normalize().mul(Math.min(toward.length(), TileGrid.TILE_SIZE)));
        return nearest(withinATile, NEAR_EXTENTS, filter);
    }

    private List<Long> polysBetween(FindNearestPolyResult start, FindNearestPolyResult end, QueryFilter filter) {
        Result<List<Long>> found = query.findPath(start.getNearestRef(), end.getNearestRef(),
                start.getNearestPos(), end.getNearestPos(), filter);
        if (found.failed() || found.result == null || found.result.isEmpty()) return null;
        return found.result;
    }

    private List<StraightPathItem> straightPath(FindNearestPolyResult start, FindNearestPolyResult end, List<Long> polys, int options) {
        Result<List<StraightPathItem>> found = query.findStraightPath(start.getNearestPos(), end.getNearestPos(),
                polys, MAX_STRAIGHT_PATH_POINTS, options);
        return found.failed() ? null : found.result;
    }

    private List<Waypoint> waypoints(List<StraightPathItem> straightPath, double spacing, QueryFilter filter) {
        List<Waypoint> waypoints = new ArrayList<>(straightPath.size());
        StraightPathItem first = straightPath.getFirst();
        waypoints.add(new Waypoint(vector(first.getPos()), Waypoint.Action.WALK, areas.label(areaOf(first.getRef()))));
        for (int i = 1; i < straightPath.size(); i++) {
            StraightPathItem before = straightPath.get(i - 1);
            Vector3 at = vector(straightPath.get(i).getPos());
            String label = areas.label(areaOf(before.getRef()));
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
        int pieces = (int) Math.ceil(across.length() / spacing - SPACING_ROUNDING_SLACK);
        for (int piece = 1; piece < pieces; piece++) {
            Vector3 between = from.add(across.mul((double) piece / pieces));
            FindNearestPolyResult ground = nearest(between, NEAR_EXTENTS, filter);
            if (ground != null) between = new Vector3(between.x(), ground.getNearestPos()[1], between.z());
            waypoints.add(new Waypoint(between, Waypoint.Action.WALK, label));
        }
    }

    private static boolean crossesLink(StraightPathItem point) {
        return (point.getFlags() & NavMeshQuery.DT_STRAIGHTPATH_OFFMESH_CONNECTION) != 0;
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
        if (ref == 0) return AreaLabels.GROUND;
        Result<Tupple2<MeshTile, Poly>> found = mesh.getTileAndPolyByRef(ref);
        return found.failed() || found.result == null ? AreaLabels.GROUND : found.result.second.getArea();
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

    private boolean straightLine(FindNearestPolyResult start, FindNearestPolyResult end, QueryFilter filter) {
        Result<RaycastHit> ray = query.raycast(start.getNearestRef(), start.getNearestPos(), end.getNearestPos(), filter, 0, 0);
        return ray.succeeded() && ray.result != null && ray.result.t >= 1;
    }

    private static double flatLength(List<StraightPathItem> straightPath) {
        double length = 0;
        for (int i = 1; i < straightPath.size(); i++) {
            float[] before = straightPath.get(i - 1).getPos();
            float[] after = straightPath.get(i).getPos();
            length += Math.hypot(after[0] - before[0], after[2] - before[2]);
        }
        return length;
    }

    private static boolean reached(Vector3 last, Vector3 to) {
        double across = new Vector3(last.x() - to.x(), 0, last.z() - to.z()).length();
        return across < ARRIVED_WITHIN_METRES && Math.abs(last.y() - to.y()) < ARRIVED_WITHIN_HEIGHT;
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
}
