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

    private record Pane(CFrame frame, Vector3 half, Vector3 eye) {}

    private static final double FLAT = 1.0e-9;
    private static final List<Pane> PANES = new ArrayList<>();

    private GlassCover() {}

    static void gather(InstanceTree tree, Vector3 eye, float partialTick) {
        PANES.clear();
        Motion motion = ClientScene.motion();
        for (Part part : tree.ofClass(Classes.PART)) {
            if (!Parts.seeThrough(part)) continue;
            CFrame frame = motion.sample(part, partialTick);
            PANES.add(new Pane(frame, part.size.mul(0.5), frame.pointToObject(eye)));
        }
    }

    static void clear() {
        PANES.clear();
    }

    static boolean any() {
        return !PANES.isEmpty();
    }

    static boolean behind(Vector3 at) {
        for (Pane pane : PANES) {
            if (crosses(pane, pane.frame().pointToObject(at))) return true;
        }
        return false;
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
