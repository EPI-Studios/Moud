package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class Adornees {

    public record Box(CFrame frame, Vector3 size) {}

    private Adornees() {}

    public static Instance target(Instance effect, Instance adornee) {
        return adornee != null && adornee.isAlive() ? adornee : effect.parent();
    }

    public static List<Part> parts(Instance target) {
        List<Part> out = new ArrayList<>();
        if (target != null) collect(target, out);
        return out;
    }

    private static void collect(Instance at, List<Part> out) {
        if (at instanceof Part part) out.add(part);
        for (Instance child : at.children()) collect(child, out);
    }

    public static Box box(Instance target, Function<Instance, CFrame> world) {
        if (target instanceof Part part) return new Box(world.apply(part), part.size);
        List<Part> parts = parts(target);
        if (parts.isEmpty()) return null;
        CFrame frame = target instanceof Spatial ? world.apply(target) : CFrame.at(world.apply(parts.getFirst()).position());
        frame = CFrame.IDENTITY.withRotation(frame.rotation());
        double[] low = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] high = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (Part part : parts) {
            CFrame placed = world.apply(part);
            Vector3 half = part.size.mul(0.5);
            for (int corner = 0; corner < 8; corner++) {
                Vector3 offset = new Vector3((corner & 1) == 0 ? -half.x() : half.x(),
                        (corner & 2) == 0 ? -half.y() : half.y(), (corner & 4) == 0 ? -half.z() : half.z());
                Vector3 local = frame.pointToObject(placed.pointToWorld(offset));
                low[0] = Math.min(low[0], local.x());
                low[1] = Math.min(low[1], local.y());
                low[2] = Math.min(low[2], local.z());
                high[0] = Math.max(high[0], local.x());
                high[1] = Math.max(high[1], local.y());
                high[2] = Math.max(high[2], local.z());
            }
        }
        Vector3 centre = new Vector3((low[0] + high[0]) / 2, (low[1] + high[1]) / 2, (low[2] + high[2]) / 2);
        Vector3 size = new Vector3(high[0] - low[0], high[1] - low[1], high[2] - low[2]);
        return new Box(frame.withPosition(frame.pointToWorld(centre)), size);
    }
}
