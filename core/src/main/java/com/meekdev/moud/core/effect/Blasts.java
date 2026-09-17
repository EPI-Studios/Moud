package com.meekdev.moud.core.effect;

import com.meekdev.moud.core.character.Character;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class Blasts {

    public record Caught(Part part, double distance) {}

    public static final double PRESSURE_PER_SPEED = 25000;

    private Blasts() {}

    public static List<Caught> caught(InstanceTree tree, Vector3 centre, double radius, Function<Instance, CFrame> world) {
        List<Caught> found = new ArrayList<>();
        if (radius <= 0) return found;
        for (Part part : tree.ofClass(Classes.PART)) {
            if (Instance.outOfWorld(part) || bodyPart(part)) continue;
            double distance = distance(centre, world.apply(part), part.size);
            if (distance <= radius) found.add(new Caught(part, distance));
        }
        return found;
    }

    private static boolean bodyPart(Instance part) {
        for (Instance at = part.parent(); at != null; at = at.parent()) {
            if (at instanceof Character) return true;
        }
        return false;
    }

    public static double falloff(double distance, double radius) {
        if (radius <= 0) return 0;
        return Math.clamp(1 - distance / radius, 0, 1);
    }

    public static double speed(double pressure, double distance, double radius) {
        return Math.max(0, pressure) * falloff(distance, radius) / PRESSURE_PER_SPEED;
    }

    public static Vector3 push(Vector3 centre, Vector3 at, double pressure, double radius) {
        Vector3 away = at.sub(centre);
        double distance = away.length();
        double speed = speed(pressure, distance, radius);
        if (speed <= 0) return Vector3.ZERO;
        return (distance < 1.0e-6 ? Vector3.UP : away.mul(1 / distance)).mul(speed);
    }

    public static boolean breaksJoints(double distance, double radius, double percent) {
        return distance <= radius * Math.clamp(percent, 0, 1);
    }

    public static double distance(Vector3 centre, CFrame frame, Vector3 size) {
        Vector3 local = frame.pointToObject(centre);
        double x = local.x() - Math.clamp(local.x(), -size.x() / 2, size.x() / 2);
        double y = local.y() - Math.clamp(local.y(), -size.y() / 2, size.y() / 2);
        double z = local.z() - Math.clamp(local.z(), -size.z() / 2, size.z() / 2);
        return Math.sqrt(x * x + y * y + z * z);
    }
}
