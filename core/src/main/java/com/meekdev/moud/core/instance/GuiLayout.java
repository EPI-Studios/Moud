package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// where each rectangle of an interface goes, and which plane a surface of one lies on
public final class GuiLayout {

    // pixels, y down, from the top left of the surface
    public record Box(double x, double y, double w, double h) {}

    // a flat rectangle in the world. right and up are how its reader sees it
    public record Plane(Vec3 centre, Vec3 right, Vec3 up, double width, double height) {}

    // clears the face of the part by a hair so the two never fight over the same depth
    public static final double LIFT = 0.002;

    private GuiLayout() {}

    public static Box place(GuiObject object, Box parent) {
        double w = object.size.x(parent.w());
        double h = object.size.y(parent.h());
        double x = parent.x() + object.position.x(parent.w()) - object.anchorX * w;
        double y = parent.y() + object.position.y(parent.h()) - object.anchorY * h;
        return new Box(x, y, w, h);
    }

    // back to front: lowest zIndex first, ties in the order they were added
    public static List<GuiObject> drawOrder(Instance parent) {
        List<GuiObject> order = new ArrayList<>();
        for (Instance child : parent.children()) {
            if (child instanceof GuiObject object) order.add(object);
        }
        order.sort(Comparator.comparingInt(object -> object.zIndex));
        return order;
    }

    // what a billboard or surface is stuck to: its adornee, or failing that its parent
    public static Instance adornee(Instance gui) {
        Instance chosen = switch (gui) {
            case BillboardGui billboard -> billboard.adornee;
            case SurfaceGui surface -> surface.adornee;
            default -> null;
        };
        return chosen != null && chosen.isAlive() ? chosen : gui.parent();
    }

    // one face of a box whose middle is at world, sized size
    public static Plane face(CFrame world, Vec3 size, SurfaceFace face) {
        Vec3 x = world.rotation().rotate(Vec3.RIGHT);
        Vec3 y = world.rotation().rotate(Vec3.UP);
        Vec3 z = world.rotation().rotate(new Vec3(0, 0, 1));
        return switch (face) {
            case FRONT -> plane(world, z.mul(-1), x.mul(-1), y, size.z(), size.x(), size.y());
            case BACK -> plane(world, z, x, y, size.z(), size.x(), size.y());
            case RIGHT -> plane(world, x, z.mul(-1), y, size.x(), size.z(), size.y());
            case LEFT -> plane(world, x.mul(-1), z, y, size.x(), size.z(), size.y());
            case TOP -> plane(world, y, x, z.mul(-1), size.y(), size.x(), size.z());
            case BOTTOM -> plane(world, y.mul(-1), x, z, size.y(), size.x(), size.z());
        };
    }

    private static Plane plane(CFrame world, Vec3 out, Vec3 right, Vec3 up, double depth,
                               double width, double height) {
        Vec3 centre = world.position().add(out.mul(depth * 0.5 + LIFT));
        return new Plane(centre, right, up, width, height);
    }
}
