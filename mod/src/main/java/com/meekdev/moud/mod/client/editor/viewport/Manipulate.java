package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.InstanceRef;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SetProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public final class Manipulate {

    private Manipulate() {}

    public static void rotate90(SceneDocument document, int axis) {
        Quat turn = Quat.axisAngle(unit(axis), Math.PI / 2);
        List<Edit> edits = new ArrayList<>();
        for (Instance instance : spatialRoots(document)) {
            CFrame world = Transforms.world(instance);
            edits.addAll(Frames.worldWrites(document, instance, world.withRotation(turn.mul(world.rotation())), null));
        }
        if (!edits.isEmpty()) document.history().execute(new Batch("Rotate 90°", edits));
    }

    public static void mirror(SceneDocument document, int axis, boolean copy) {
        List<Instance> roots = spatialRoots(document);
        if (roots.isEmpty()) return;
        Vector3 normal = unit(axis);
        Vector3 centre = copy ? edge(roots, normal) : centre(roots);
        UnaryOperator<CFrame> reflect = world -> {
            Vector3 p = world.position();
            Vector3 mirrored = p.sub(normal.mul(2 * p.sub(centre).dot(normal)));
            Quat q = world.rotation();
            Quat flipped = switch (axis) {
                case 0 -> new Quat(q.x(), -q.y(), -q.z(), q.w());
                case 1 -> new Quat(-q.x(), q.y(), -q.z(), q.w());
                default -> new Quat(-q.x(), -q.y(), q.z(), q.w());
            };
            return new CFrame(mirrored, flipped);
        };
        if (copy) {
            document.pasteTransformed(document.selectedRoots(), reflect, "Mirror copy", true);
            return;
        }
        List<Edit> edits = new ArrayList<>();
        for (Instance instance : roots) edits.addAll(Frames.worldWrites(document, instance, reflect.apply(Transforms.world(instance)), null));
        document.history().execute(new Batch("Mirror", edits));
    }

    public static void array(SceneDocument document, int count, Vector3 offset, double degreesAroundY) {
        List<Instance> roots = spatialRoots(document);
        if (roots.isEmpty() || count < 1) return;
        Vector3 centre = centre(roots);
        List<InstanceRef> refs = document.selectedRoots();
        for (int n = 1; n <= count; n++) {
            int step = n;
            Quat turn = Quat.axisAngle(new Vector3(0, 1, 0), Math.toRadians(degreesAroundY * step));
            document.pasteTransformed(refs, world -> {
                Vector3 around = turn.rotate(world.position().sub(centre)).add(centre).add(offset.mul(step));
                return new CFrame(around, turn.mul(world.rotation()));
            }, "Array", false);
        }
    }

    public static void lock(SceneDocument document, boolean locked) {
        List<Edit> edits = new ArrayList<>();
        for (int id : document.selection().all()) {
            if (!(document.find(id) instanceof Part part) || part.locked == locked) continue;
            edits.add(new SetProperty(document.ref(id), part.def().property("locked").index(), locked, locked ? "Lock" : "Unlock"));
        }
        if (!edits.isEmpty()) document.history().execute(new Batch(locked ? "Lock" : "Unlock", edits));
    }

    public static void paint(SceneDocument document, Part part, Color colour) {
        if (part.color.equals(colour)) return;
        document.history().execute(new SetProperty(document.ref(part.id()), part.def().property("color").index(), colour, "Paint"));
    }

    static List<Instance> spatialRoots(SceneDocument document) {
        List<Instance> roots = new ArrayList<>();
        for (InstanceRef ref : document.selectedRoots()) {
            if (document.find(ref) instanceof Spatial spatial && document.pickable(spatial)) roots.add(spatial);
        }
        return roots;
    }

    static Vector3 centre(List<Instance> instances) {
        double[] box = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (Instance instance : instances) bound(instance, box);
        if (box[0] > box[3]) return Vector3.ZERO;
        return new Vector3(box[0] + box[3], box[1] + box[4], box[2] + box[5]).mul(0.5);
    }

    private static void bound(Instance instance, double[] box) {
        if (instance instanceof Part part) {
            for (Vector3 corner : Frames.corners(part)) grow(box, corner);
        } else if (instance instanceof Spatial) {
            grow(box, Transforms.world(instance).position());
        }
        for (Instance child : instance.children()) bound(child, box);
    }

    private static void grow(double[] box, Vector3 at) {
        box[0] = Math.min(box[0], at.x());
        box[1] = Math.min(box[1], at.y());
        box[2] = Math.min(box[2], at.z());
        box[3] = Math.max(box[3], at.x());
        box[4] = Math.max(box[4], at.y());
        box[5] = Math.max(box[5], at.z());
    }

    static Vector3 origin(Instance instance) {
        return Transforms.world(instance).pointToWorld(((Spatial) instance).pivot);
    }

    private static Vector3 edge(List<Instance> roots, Vector3 normal) {
        double far = Double.NEGATIVE_INFINITY;
        for (Instance instance : roots) {
            double at = Transforms.world(instance).position().dot(normal);
            double reach = instance instanceof Part part ? Frames.extentAlong(part, normal) : 0;
            far = Math.max(far, at + reach);
        }
        return normal.mul(far);
    }

    static Vector3 unit(int axis) {
        return switch (axis) {
            case 0 -> new Vector3(1, 0, 0);
            case 1 -> new Vector3(0, 1, 0);
            default -> new Vector3(0, 0, 1);
        };
    }
}
