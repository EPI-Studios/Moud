package com.meekdev.moud.core.ui;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.UDim2;
import com.meekdev.moud.core.math.Vector3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GuiLayout {

    public record Box(double x, double y, double w, double h) {}

    public record Size(double w, double h) {}

    public record Placed(GuiObject object, Box box) {}

    public record Plane(Vector3 centre, Vector3 right, Vector3 up, double width, double height) {}

    @FunctionalInterface
    public interface Measure {
        Size text(TextLabel label, double wrapWidth);
    }

    private record Arranged(List<Placed> placed, double w, double h) {}

    public static final double LIFT = 0.002;

    public static final Measure NO_TEXT = (label, width) -> new Size(0, 0);

    private GuiLayout() {}

    public static Box place(GuiObject object, Box parent) {
        return position(object, parent, object.size.x(parent.w()), object.size.y(parent.h()));
    }

    public static List<Placed> arrange(Instance parent, Box box, Measure measure) {
        Box content = parent instanceof ScrollingFrame frame ? canvas(frame, box, measure) : box;
        return children(parent, inset(parent, content), measure).placed();
    }

    public static Size size(GuiObject object, double parentW, double parentH, Measure measure) {
        return constrain(object, object.size.x(parentW), object.size.y(parentH), measure);
    }

    public static Size extent(GuiObject object, double w, double h, Measure measure) {
        Box inner = inset(object, new Box(0, 0, w, h));
        double ew = 0;
        double eh = 0;
        if (object instanceof TextLabel label && !label.text.isEmpty()) {
            double wrap = label.textWrapped && !object.automaticSize.x() ? inner.w() : Double.POSITIVE_INFINITY;
            Size text = measure.text(label, wrap);
            ew = text.w();
            eh = text.h();
        }
        Arranged inside = children(object, inner, measure);
        ew = Math.max(ew, inside.w());
        eh = Math.max(eh, inside.h());
        return new Size(ew + w - inner.w(), eh + h - inner.h());
    }

    public static Box canvas(ScrollingFrame frame, Box window, Measure measure) {
        double w = Math.max(window.w(), frame.canvasSize.x(window.w()));
        double h = Math.max(window.h(), frame.canvasSize.y(window.h()));
        if (frame.automaticCanvasSize != AutomaticSize.NONE) {
            Size needed = contentOf(frame, w, h, measure);
            if (frame.automaticCanvasSize.x()) w = Math.max(w, needed.w());
            if (frame.automaticCanvasSize.y()) h = Math.max(h, needed.h());
        }
        Vector3 at = frame.clamp(frame.canvasPosition, w, h, window.w(), window.h());
        return new Box(window.x() - at.x(), window.y() - at.y(), w, h);
    }

    public static Box inset(Instance object, Box box) {
        UIPadding padding = component(object, UIPadding.class);
        if (padding == null) return box;
        double left = udim(padding.paddingLeft, false, box.w());
        double right = udim(padding.paddingRight, false, box.w());
        double top = udim(padding.paddingTop, true, box.h());
        double bottom = udim(padding.paddingBottom, true, box.h());
        return new Box(box.x() + left, box.y() + top, Math.max(0, box.w() - left - right), Math.max(0, box.h() - top - bottom));
    }

    public static double cornerRadius(GuiObject object, double w, double h) {
        UICorner corner = component(object, UICorner.class);
        double shortest = Math.min(w, h);
        double radius = corner == null ? object.cornerRadius : udim(corner.cornerRadius, false, shortest);
        return Math.clamp(radius, 0, Math.max(0, shortest * 0.5));
    }

    public static double scale(GuiObject object) {
        UIScale scale = component(object, UIScale.class);
        return scale == null ? 1 : scale.scale;
    }

    public static double udim(UDim2 value, boolean vertical, double parent) {
        return vertical ? value.yScale() * parent + value.yOffset() : value.xScale() * parent + value.xOffset();
    }

    public static <T extends Instance> T component(Instance at, Class<T> type) {
        for (Instance child : at.children()) {
            if (type.isInstance(child)) return type.cast(child);
        }
        return null;
    }

    public static List<GuiObject> drawOrder(Instance parent) {
        List<GuiObject> order = new ArrayList<>();
        for (Instance child : parent.children()) {
            if (child instanceof GuiObject object) order.add(object);
        }
        order.sort(Comparator.comparingInt(object -> object.zIndex));
        return order;
    }

    private static Size contentOf(Instance parent, double w, double h, Measure measure) {
        Box inner = inset(parent, new Box(0, 0, w, h));
        Arranged inside = children(parent, inner, measure);
        return new Size(inside.w() + w - inner.w(), inside.h() + h - inner.h());
    }

    private static Size constrain(GuiObject object, double w, double h, Measure measure) {
        if (object.automaticSize != AutomaticSize.NONE) {
            Size needed = extent(object, Math.max(0, w), Math.max(0, h), measure);
            if (object.automaticSize.x()) w = Math.max(w, needed.w());
            if (object.automaticSize.y()) h = Math.max(h, needed.h());
        }
        UIAspectRatioConstraint aspect = component(object, UIAspectRatioConstraint.class);
        if (aspect != null) {
            double ratio = aspect.aspectRatio;
            if (aspect.aspectType == AspectType.FIT_WITHIN_MAX_SIZE) {
                if (w / Math.max(h, 1e-9) > ratio) w = h * ratio;
                else h = w / ratio;
            } else if (aspect.dominantAxis == DominantAxis.WIDTH) {
                h = w / ratio;
            } else {
                w = h * ratio;
            }
        }
        UISizeConstraint limits = component(object, UISizeConstraint.class);
        if (limits != null) {
            w = limit(w, limits.minSize.x(), limits.maxSize.x());
            h = limit(h, limits.minSize.y(), limits.maxSize.y());
        }
        return new Size(Math.max(0, w), Math.max(0, h));
    }

    private static double limit(double value, double min, double max) {
        double upper = max > 0 ? max : Double.POSITIVE_INFINITY;
        return Math.max(Math.min(value, upper), min);
    }

    private static Box position(GuiObject object, Box parent, double w, double h) {
        double x = parent.x() + object.position.x(parent.w()) - object.anchorX * w;
        double y = parent.y() + object.position.y(parent.h()) - object.anchorY * h;
        return new Box(x, y, w, h);
    }

    private static Arranged children(Instance parent, Box content, Measure measure) {
        List<GuiObject> items = new ArrayList<>();
        for (Instance child : parent.children()) {
            if (child instanceof GuiObject object && object.visible) items.add(object);
        }
        UILayout layout = component(parent, UILayout.class);
        return switch (layout) {
            case UIListLayout list -> list(list, sorted(list, items), content, measure);
            case UIGridLayout grid -> grid(grid, sorted(grid, items), content);
            case null, default -> free(items, content, measure);
        };
    }

    private static Arranged free(List<GuiObject> items, Box content, Measure measure) {
        List<Placed> placed = new ArrayList<>(items.size());
        double right = 0;
        double bottom = 0;
        for (GuiObject object : items) {
            Size size = size(object, content.w(), content.h(), measure);
            Box box = position(object, content, size.w(), size.h());
            placed.add(new Placed(object, box));
            right = Math.max(right, box.x() + box.w() - content.x());
            bottom = Math.max(bottom, box.y() + box.h() - content.y());
        }
        return new Arranged(placed, right, bottom);
    }

    private static List<GuiObject> sorted(UILayout layout, List<GuiObject> items) {
        List<GuiObject> order = new ArrayList<>(items);
        if (layout.sortOrder == SortOrder.NAME) order.sort(Comparator.comparing(Instance::name));
        else order.sort(Comparator.comparingInt(object -> object.layoutOrder));
        return order;
    }

    private static Arranged list(UIListLayout layout, List<GuiObject> items, Box content, Measure measure) {
        boolean vertical = layout.fillDirection == FillDirection.VERTICAL;
        double mainSpace = vertical ? content.h() : content.w();
        double crossSpace = vertical ? content.w() : content.h();
        double gap = udim(layout.padding, vertical, mainSpace);
        double crossGap = udim(layout.padding, !vertical, crossSpace);
        double mainAlign = vertical ? factor(layout.verticalAlignment) : factor(layout.horizontalAlignment);
        double crossAlign = vertical ? factor(layout.horizontalAlignment) : factor(layout.verticalAlignment);

        List<Size> sizes = new ArrayList<>(items.size());
        for (GuiObject object : items) sizes.add(size(object, content.w(), content.h(), measure));

        List<int[]> lines = new ArrayList<>();
        List<double[]> extents = new ArrayList<>();
        int start = 0;
        double used = 0;
        double thick = 0;
        for (int n = 0; n < items.size(); n++) {
            double along = vertical ? sizes.get(n).h() : sizes.get(n).w();
            double across = vertical ? sizes.get(n).w() : sizes.get(n).h();
            if (layout.wraps && n > start && used + gap + along > mainSpace + 1e-9) {
                lines.add(new int[] {start, n});
                extents.add(new double[] {used, thick});
                start = n;
                used = 0;
                thick = 0;
            }
            used += (n > start ? gap : 0) + along;
            thick = Math.max(thick, across);
        }
        if (!items.isEmpty()) {
            lines.add(new int[] {start, items.size()});
            extents.add(new double[] {used, thick});
        }

        double longest = 0;
        double total = 0;
        for (int l = 0; l < lines.size(); l++) {
            longest = Math.max(longest, extents.get(l)[0]);
            total += (l > 0 ? crossGap : 0) + extents.get(l)[1];
        }

        List<Placed> placed = new ArrayList<>(items.size());
        double crossAt = layout.wraps ? (crossSpace - total) * crossAlign : 0;
        for (int l = 0; l < lines.size(); l++) {
            double lineThick = layout.wraps ? extents.get(l)[1] : crossSpace;
            double mainAt = (mainSpace - extents.get(l)[0]) * mainAlign;
            for (int n = lines.get(l)[0]; n < lines.get(l)[1]; n++) {
                Size size = sizes.get(n);
                double along = vertical ? size.h() : size.w();
                double across = vertical ? size.w() : size.h();
                double crossOffset = crossAt + (lineThick - across) * crossAlign;
                Box box = vertical
                        ? new Box(content.x() + crossOffset, content.y() + mainAt, size.w(), size.h())
                        : new Box(content.x() + mainAt, content.y() + crossOffset, size.w(), size.h());
                placed.add(new Placed(items.get(n), box));
                mainAt += along + gap;
            }
            crossAt += extents.get(l)[1] + crossGap;
        }
        return vertical ? new Arranged(placed, total, longest) : new Arranged(placed, longest, total);
    }

    private static Arranged grid(UIGridLayout layout, List<GuiObject> items, Box content) {
        double cellW = Math.max(0, layout.cellSize.x(content.w()));
        double cellH = Math.max(0, layout.cellSize.y(content.h()));
        double padX = layout.cellPadding.x(content.w());
        double padY = layout.cellPadding.y(content.h());
        boolean horizontal = layout.fillDirection == FillDirection.HORIZONTAL;

        int count = items.size();
        int across = horizontal
                ? (int) Math.floor((content.w() + padX) / Math.max(cellW + padX, 1e-9))
                : (int) Math.floor((content.h() + padY) / Math.max(cellH + padY, 1e-9));
        across = Math.max(1, across);
        if (layout.fillDirectionMaxCells > 0) across = Math.min(across, layout.fillDirectionMaxCells);
        int filled = Math.min(count, across);
        int stacked = count == 0 ? 0 : (count + across - 1) / across;
        int columns = horizontal ? filled : stacked;
        int rows = horizontal ? stacked : filled;

        double blockW = columns == 0 ? 0 : columns * cellW + (columns - 1) * padX;
        double blockH = rows == 0 ? 0 : rows * cellH + (rows - 1) * padY;
        double left = content.x() + (content.w() - blockW) * factor(layout.horizontalAlignment);
        double top = content.y() + (content.h() - blockH) * factor(layout.verticalAlignment);
        boolean fromRight = layout.startCorner == StartCorner.TOP_RIGHT || layout.startCorner == StartCorner.BOTTOM_RIGHT;
        boolean fromBottom = layout.startCorner == StartCorner.BOTTOM_LEFT || layout.startCorner == StartCorner.BOTTOM_RIGHT;

        List<Placed> placed = new ArrayList<>(count);
        for (int n = 0; n < count; n++) {
            int column = horizontal ? n % across : n / across;
            int row = horizontal ? n / across : n % across;
            if (fromRight) column = columns - 1 - column;
            if (fromBottom) row = rows - 1 - row;
            Box box = new Box(left + column * (cellW + padX), top + row * (cellH + padY), cellW, cellH);
            placed.add(new Placed(items.get(n), box));
        }
        return new Arranged(placed, blockW, blockH);
    }

    private static double factor(HorizontalAlign align) {
        return switch (align) {
            case LEFT -> 0;
            case CENTER -> 0.5;
            case RIGHT -> 1;
        };
    }

    private static double factor(VerticalAlign align) {
        return switch (align) {
            case TOP -> 0;
            case CENTER -> 0.5;
            case BOTTOM -> 1;
        };
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

    public static boolean interfacePart(Instance instance) {
        return instance instanceof GuiObject || instance instanceof ScreenGui || instance instanceof BillboardGui
                || instance instanceof SurfaceGui || instance instanceof UIComponent;
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
