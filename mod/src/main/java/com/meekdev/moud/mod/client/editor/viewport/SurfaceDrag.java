package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.mod.adapter.physics.BlockRays;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.InstanceRef;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.script.api.BlockRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

final class SurfaceDrag {

    private static final double REACH = 512.0;
    private static final Vector3 UP = new Vector3(0, 1, 0);
    private static final BlockRays BLOCKS = new BlockRays(() -> Minecraft.getInstance().level, false);

    private final Map<Integer, CFrame> starts = new LinkedHashMap<>();
    private final Set<Integer> moving = new HashSet<>();
    private @Nullable Part leader;
    private Vector3 grab = Vector3.ZERO;
    private boolean started;

    boolean armed() {
        return leader != null;
    }

    boolean started() {
        return started;
    }

    void arm(SceneDocument document, Part picked, Vector3 grabPoint) {
        cancel();
        leader = picked;
        grab = grabPoint.sub(Transforms.world(picked).position());
        for (InstanceRef root : document.selectedRoots()) {
            Instance instance = document.find(root);
            if (!(instance instanceof Spatial)) continue;
            starts.put(instance.id(), Transforms.world(instance));
            mark(instance);
        }
        if (!starts.containsKey(picked.id())) {
            starts.clear();
            moving.clear();
            starts.put(picked.id(), Transforms.world(picked));
            mark(picked);
        }
    }

    void cancel() {
        leader = null;
        started = false;
        starts.clear();
        moving.clear();
    }

    void update(SceneDocument document, SceneView view, float mouseX, float mouseY, boolean snap, double step) {
        Part part = leader;
        Instance world = document.world();
        if (part == null || world == null) return;
        started = true;
        Vector3 from = view.cameraPosition();
        Vector3 direction = view.rayDirection(mouseX, mouseY);
        double best = REACH;
        Vector3 normal = null;
        Queries.Cast cast = Queries.raycast(world, from, direction, REACH, candidate -> !moving.contains(candidate.id()));
        if (cast != null) {
            best = cast.distance();
            normal = cast.normal();
        }
        BlockRef.Hit block = BLOCKS.raycast(from, direction, best);
        if (block != null && block.distance() < best) {
            best = block.distance();
            normal = block.normal();
        }
        CFrame leaderStart = starts.get(part.id());
        Vector3 target;
        if (normal == null) {
            Vector3 floor = floorHit(from, direction, leaderStart.position().y() - Frames.extentAlong(part, UP));
            if (floor == null) return;
            normal = UP;
            target = floor;
        } else {
            target = from.add(direction.mul(best));
        }
        normal = dominant(normal);
        Vector3 center = target.add(normal.mul(Frames.extentAlong(part, normal))).sub(grab.sub(normal.mul(normal.dot(grab))));
        if (snap) {
            center = new Vector3(normal.x() == 0 ? Rays.snap(center.x(), step) : center.x(),
                    normal.y() == 0 ? Rays.snap(center.y(), step) : center.y(),
                    normal.z() == 0 ? Rays.snap(center.z(), step) : center.z());
        }
        Vector3 delta = center.sub(leaderStart.position());
        List<Edit> edits = new ArrayList<>();
        for (Map.Entry<Integer, CFrame> entry : starts.entrySet()) {
            Instance instance = document.find(entry.getKey());
            if (instance == null) continue;
            CFrame start = entry.getValue();
            edits.addAll(Frames.worldWrites(document, instance, start.withPosition(start.position().add(delta)), null));
        }
        if (!edits.isEmpty()) document.history().execute(new Batch("Move", edits));
    }

    private static @Nullable Vector3 floorHit(Vector3 from, Vector3 direction, double height) {
        if (Math.abs(direction.y()) < 1.0e-6) return null;
        double t = (height - from.y()) / direction.y();
        if (t <= 0 || t > REACH) return null;
        return from.add(direction.mul(t));
    }

    private static Vector3 dominant(Vector3 normal) {
        double ax = Math.abs(normal.x());
        double ay = Math.abs(normal.y());
        double az = Math.abs(normal.z());
        if (ay >= ax && ay >= az) return new Vector3(0, Math.signum(normal.y()), 0);
        if (ax >= az) return new Vector3(Math.signum(normal.x()), 0, 0);
        return new Vector3(0, 0, Math.signum(normal.z()));
    }

    private void mark(Instance instance) {
        moving.add(instance.id());
        for (Instance child : instance.children()) mark(child);
    }
}
