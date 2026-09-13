package com.meekdev.moud.core.instance;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GuiLayout {

    public record Box(double x, double y, double w, double h) {}

    public record Plane(Vector3 centre, Vector3 right, Vector3 up, double width, double height) {}

    public static final double LIFT = 0.002;

    private GuiLayout() {}

    public static Box place(GuiObject object, Box parent) {
        double w = object.size.x(parent.w());
        double h = object.size.y(parent.h());
        double x = parent.x() + object.position.x(parent.w()) - object.anchorX * w;
        double y = parent.y() + object.position.y(parent.h()) - object.anchorY * h;
        return new Box(x, y, w, h);
    }

    public static List<GuiObject> drawOrder(Instance parent) {
        List<GuiObject> order = new ArrayList<>();
        for (Instance child : parent.children()) {
            if (child instanceof GuiObject object) order.add(object);
        }
        order.sort(Comparator.comparingInt(object -> object.zIndex));
        return order;
    }

    public static String font(TextLabel label) {
        for (Instance at = label; at != null; at = at.parent()) {
            String chosen = switch (at) {
                case TextLabel text -> text.font;
                case ScreenGui screen -> screen.font;
                case BillboardGui billboard -> billboard.font;
                case SurfaceGui surface -> surface.font;
                default -> "";
            };
            if (!chosen.isEmpty()) return chosen;
        }
        return "";
    }

    public static Instance adornee(Instance gui) {
        Instance chosen = switch (gui) {
            case BillboardGui billboard -> billboard.adornee;
            case SurfaceGui surface -> surface.adornee;
            default -> null;
        };
        return chosen != null && chosen.isAlive() ? chosen : gui.parent();
    }

    public static Plane face(CFrame world, Vector3 size, SurfaceFace face) {
        Vector3 x = world.rotation().rotate(Vector3.RIGHT);
        Vector3 y = world.rotation().rotate(Vector3.UP);
        Vector3 z = world.rotation().rotate(new Vector3(0, 0, 1));
        return switch (face) {
            case FRONT -> plane(world, z.mul(-1), x.mul(-1), y, size.z(), size.x(), size.y());
            case BACK -> plane(world, z, x, y, size.z(), size.x(), size.y());
            case RIGHT -> plane(world, x, z.mul(-1), y, size.x(), size.z(), size.y());
            case LEFT -> plane(world, x.mul(-1), z, y, size.x(), size.z(), size.y());
            case TOP -> plane(world, y, x, z.mul(-1), size.y(), size.x(), size.z());
            case BOTTOM -> plane(world, y.mul(-1), x, z, size.y(), size.x(), size.z());
        };
    }

    private static Plane plane(CFrame world, Vector3 out, Vector3 right, Vector3 up, double depth,
                               double width, double height) {
        Vector3 centre = world.position().add(out.mul(depth * 0.5 + LIFT));
        return new Plane(centre, right, up, width, height);
    }
}
