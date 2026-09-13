package com.meekdev.moud.core.nav;

import com.meekdev.moud.core.math.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

// a walk through a grid of blocks, for bodies a place moves
//
// a cell can be stood in when it and the one above are open and the one below is solid. a step is to any
// of the eight cells around, one up with room overhead, or down as far as a drop allows. diagonals only
// where both sides are open, so nothing cuts a corner through a wall
public final class GridPath {

    // whether a block cell is solid, from blocks and parts alike
    @FunctionalInterface
    public interface Terrain {
        boolean solid(int x, int y, int z);
    }

    public record Options(int maxNodes, int maxDrop, int height) {
        public static final Options DEFAULT = new Options(20000, 3, 2);
    }

    private record Node(int x, int y, int z) {}

    private static final int[][] AROUND = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private GridPath() {}

    // the waypoints from one point to another, each the middle of a cell at its floor, or null when there
    // is no way within the search budget
    public static List<Vec3> find(Terrain terrain, Vec3 from, Vec3 to, Options options) {
        Node start = standable(terrain, from, options);
        Node goal = standable(terrain, to, options);
        if (start == null || goal == null) return null;
        Map<Node, Node> cameFrom = new HashMap<>();
        Map<Node, Double> cost = new HashMap<>();
        record Open(Node node, double priority) {}
        PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.priority(), b.priority()));
        cost.put(start, 0.0);
        open.add(new Open(start, heuristic(start, goal)));
        int expanded = 0;
        while (!open.isEmpty() && expanded < options.maxNodes()) {
            Node current = open.poll().node();
            if (current.equals(goal)) return simplify(walkBack(cameFrom, current));
            expanded++;
            double here = cost.get(current);
            for (int[] step : AROUND) {
                boolean diagonal = step[0] != 0 && step[1] != 0;
                if (diagonal && (!open(terrain, current.x() + step[0], current.y(), current.z(), options)
                        || !open(terrain, current.x(), current.y(), current.z() + step[1], options))) {
                    continue;
                }
                Node next = neighbour(terrain, current, step[0], step[1], options);
                if (next == null) continue;
                double moved = here + (diagonal ? 1.4142 : 1) + Math.abs(next.y() - current.y()) * 0.5;
                Double known = cost.get(next);
                if (known != null && known <= moved) continue;
                cost.put(next, moved);
                cameFrom.put(next, current);
                open.add(new Open(next, moved + heuristic(next, goal)));
            }
        }
        return null;
    }

    // a random cell something could stand in near a point, or null after enough tries
    public static Vec3 randomNear(Terrain terrain, Vec3 centre, double radius, Options options, Random random) {
        for (int tries = 0; tries < 64; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(random.nextDouble()) * radius;
            Node node = standable(terrain, centre.add(new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r)),
                    new Options(options.maxNodes(), (int) Math.ceil(radius) + options.maxDrop(), options.height()));
            if (node != null) return middle(node);
        }
        return null;
    }

    private static Node neighbour(Terrain terrain, Node from, int dx, int dz, Options options) {
        int x = from.x() + dx;
        int z = from.z() + dz;
        if (stand(terrain, x, from.y(), z, options)) return new Node(x, from.y(), z);
        // up a step, with room above the body's head where it is now
        if (!terrain.solid(from.x(), from.y() + options.height(), from.z()) && stand(terrain, x, from.y() + 1, z, options)) {
            return new Node(x, from.y() + 1, z);
        }
        for (int drop = 1; drop <= options.maxDrop(); drop++) {
            if (!open(terrain, x, from.y() - drop + 1, z, options)) break;
            if (stand(terrain, x, from.y() - drop, z, options)) return new Node(x, from.y() - drop, z);
        }
        return null;
    }

    private static boolean open(Terrain terrain, int x, int y, int z, Options options) {
        for (int h = 0; h < options.height(); h++) {
            if (terrain.solid(x, y + h, z)) return false;
        }
        return true;
    }

    private static boolean stand(Terrain terrain, int x, int y, int z, Options options) {
        return terrain.solid(x, y - 1, z) && open(terrain, x, y, z, options);
    }

    // the cell under a point that can be stood in, looking a little up and down
    private static Node standable(Terrain terrain, Vec3 at, Options options) {
        int x = (int) Math.floor(at.x());
        int y = (int) Math.floor(at.y() + 0.01);
        int z = (int) Math.floor(at.z());
        for (int dy = 0; dy <= options.maxDrop() + 2; dy++) {
            if (stand(terrain, x, y - dy, z, options)) return new Node(x, y - dy, z);
            if (dy > 0 && dy <= 2 && stand(terrain, x, y + dy, z, options)) return new Node(x, y + dy, z);
        }
        return null;
    }

    private static double heuristic(Node a, Node b) {
        int dx = Math.abs(a.x() - b.x());
        int dz = Math.abs(a.z() - b.z());
        return Math.max(dx, dz) + 0.4142 * Math.min(dx, dz) + Math.abs(a.y() - b.y()) * 0.5;
    }

    private static List<Node> walkBack(Map<Node, Node> cameFrom, Node end) {
        List<Node> path = new ArrayList<>();
        for (Node at = end; at != null; at = cameFrom.get(at)) path.add(at);
        Collections.reverse(path);
        return path;
    }

    // straight runs on one level become their two ends
    private static List<Vec3> simplify(List<Node> nodes) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (i > 0 && i < nodes.size() - 1) {
                Node before = nodes.get(i - 1);
                Node after = nodes.get(i + 1);
                boolean straight = node.x() - before.x() == after.x() - node.x() && node.z() - before.z() == after.z() - node.z()
                        && before.y() == node.y() && node.y() == after.y();
                if (straight) continue;
            }
            out.add(middle(node));
        }
        return out;
    }

    private static Vec3 middle(Node node) {
        return new Vec3(node.x() + 0.5, node.y(), node.z() + 0.5);
    }
}
