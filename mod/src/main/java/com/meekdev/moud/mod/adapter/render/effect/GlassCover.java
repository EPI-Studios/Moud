package com.meekdev.moud.mod.adapter.render.effect;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.client.ClientScene;
import java.util.ArrayList;
import java.util.List;

final class GlassCover {

    private record Pane(CFrame frame, Vector3 half, Vector3 eye, double radius) {}

    private static final double FLAT = 1.0e-9;
    private static final List<Pane> PANES = new ArrayList<>();
    private static Vector3 seenFrom = Vector3.ZERO;

    private GlassCover() {}

    static void gather(InstanceTree tree, Vector3 eye, float partialTick) {
        PANES.clear();
        seenFrom = eye;
        Motion motion = ClientScene.motion();
        for (Part part : tree.ofClass(Classes.PART)) {
            if (!Parts.seeThrough(part)) continue;
            CFrame frame = motion.sample(part, partialTick);
            Vector3 half = part.size.mul(0.5);
            PANES.add(new Pane(frame, half, frame.pointToObject(eye), half.length()));
        }
    }

    static void clear() {
        PANES.clear();
    }

    static boolean any() {
        return !PANES.isEmpty();
    }

    static boolean behind(Vector3 at) {
        double dx = at.x() - seenFrom.x();
        double dy = at.y() - seenFrom.y();
        double dz = at.z() - seenFrom.z();
        double length = dx * dx + dy * dy + dz * dz;
        for (Pane pane : PANES) {
            if (!near(pane, dx, dy, dz, length)) continue;
            if (crosses(pane, pane.frame().pointToObject(at))) return true;
        }
        return false;
    }

    private static boolean near(Pane pane, double dx, double dy, double dz, double length) {
        Vector3 centre = pane.frame().position();
        double cx = centre.x() - seenFrom.x();
        double cy = centre.y() - seenFrom.y();
        double cz = centre.z() - seenFrom.z();
        double t = length > 0 ? Math.clamp((cx * dx + cy * dy + cz * dz) / length, 0, 1) : 0;
        double ox = cx - dx * t;
        double oy = cy - dy * t;
        double oz = cz - dz * t;
        return ox * ox + oy * oy + oz * oz <= pane.radius() * pane.radius();
    }

    private static boolean crosses(Pane pane, Vector3 to) {
        Vector3 from = pane.eye();
        Vector3 half = pane.half();
        double enter = 0;
        double leave = 1;
        for (int axis = 0; axis < 3; axis++) {
            double a = axis == 0 ? from.x() : axis == 1 ? from.y() : from.z();
            double b = axis == 0 ? to.x() : axis == 1 ? to.y() : to.z();
            double h = axis == 0 ? half.x() : axis == 1 ? half.y() : half.z();
            double d = b - a;
            if (Math.abs(d) < FLAT) {
                if (a < -h || a > h) return false;
                continue;
            }
            double t0 = (-h - a) / d;
            double t1 = (h - a) / d;
            enter = Math.max(enter, Math.min(t0, t1));
            leave = Math.min(leave, Math.max(t0, t1));
            if (enter > leave) return false;
        }
        return enter > 0 || leave < 1;
    }
}
