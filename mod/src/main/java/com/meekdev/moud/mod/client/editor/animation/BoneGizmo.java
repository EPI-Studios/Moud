package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.viewport.SceneView;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import org.jspecify.annotations.Nullable;

final class BoneGizmo {

    private static final float RING_RADIUS = 58.0f;
    private static final float ARROW_LENGTH = 66.0f;
    private static final float GRAB = 7.0f;
    private static final int SEGMENTS = 64;
    private static final double SNAP_DEGREES = 15;

    enum Mode { ROTATE, MOVE }

    record Result(Quat rotation, Vector3 position) {}

    private int hovered = -1;
    private int held = -1;
    private float pressX;
    private float pressY;
    private double startAngle;
    private @Nullable CFrame start;

    boolean dragging() {
        return held >= 0;
    }

    boolean hovering() {
        return hovered >= 0;
    }

    void cancel() {
        held = -1;
        start = null;
    }

    @Nullable Result draw(ImDrawList draw, SceneView view, CFrame camera, CFrame pivot, boolean local, Mode mode, boolean hover) {
        float[] centre = view.toScreen(pivot.position());
        if (centre == null) {
            hovered = -1;
            return null;
        }
        double perPixel = worldPerPixel(view, camera, pivot.position(), centre);
        Quat basis = local ? pivot.rotation() : Quat.IDENTITY;
        Vector3[] axes = {basis.rotate(new Vector3(1, 0, 0)), basis.rotate(new Vector3(0, 1, 0)), basis.rotate(new Vector3(0, 0, 1))};
        Vector3 toCamera = camera.position().sub(pivot.position()).normalize();
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        if (held < 0) hovered = hover ? pick(view, pivot.position(), axes, perPixel, mode, mouseX, mouseY) : -1;
        int active = held >= 0 ? held : hovered;
        if (mode == Mode.ROTATE) {
            for (int axis = 0; axis < 3; axis++) ring(draw, view, pivot.position(), axes[axis], toCamera, perPixel * EditorScale.of(RING_RADIUS), axis, axis == active);
        } else {
            for (int axis = 0; axis < 3; axis++) arrow(draw, view, pivot.position(), axes[axis], perPixel * EditorScale.of(ARROW_LENGTH), axis, axis == active, centre);
        }
        draw.addCircleFilled(centre[0], centre[1], EditorScale.of(3.5f), Paint.SELECTED);
        if (held < 0 && hovered >= 0 && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            held = hovered;
            pressX = mouseX;
            pressY = mouseY;
            start = pivot;
            startAngle = Math.atan2(mouseY - centre[1], mouseX - centre[0]);
        }
        if (held < 0 || start == null) return null;
        if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            cancel();
            return null;
        }
        Vector3 axis = axes[held];
        if (mode == Mode.ROTATE) {
            double angle = Math.atan2(mouseY - centre[1], mouseX - centre[0]) - startAngle;
            if (axis.dot(toCamera) > 0) angle = -angle;
            if (ImGui.getIO().getKeyCtrl()) angle = Math.toRadians(Math.round(Math.toDegrees(angle) / SNAP_DEGREES) * SNAP_DEGREES);
            Quat turned = Quat.axisAngle(axis, angle).mul(start.rotation()).normalize();
            guide(draw, centre, mouseX, mouseY, held);
            return new Result(turned, start.position());
        }
        float[] tip = view.toScreen(start.position().add(axis));
        if (tip == null) return null;
        double dx = tip[0] - centre[0];
        double dy = tip[1] - centre[1];
        double length = Math.hypot(dx, dy);
        if (length < 1e-3) return null;
        double along = ((mouseX - pressX) * dx + (mouseY - pressY) * dy) / (length * length);
        if (ImGui.getIO().getKeyCtrl()) along = Math.round(along * 16) / 16.0;
        return new Result(start.rotation(), start.position().add(axis.mul(along)));
    }

    private static void guide(ImDrawList draw, float[] centre, float mouseX, float mouseY, int axis) {
        draw.addLine(centre[0], centre[1], mouseX, mouseY, EditorStyle.withAlpha(Paint.axis(axis), 0.5f), EditorScale.of(1));
    }

    private static double worldPerPixel(SceneView view, CFrame camera, Vector3 at, float[] centre) {
        float[] side = view.toScreen(at.add(camera.rightVector()));
        if (side == null) return 0.01;
        double pixels = Math.hypot(side[0] - centre[0], side[1] - centre[1]);
        return pixels < 1e-3 ? 0.01 : 1.0 / pixels;
    }

    private int pick(SceneView view, Vector3 at, Vector3[] axes, double perPixel, Mode mode, float mouseX, float mouseY) {
        int best = -1;
        double closest = EditorScale.of(GRAB);
        for (int axis = 0; axis < 3; axis++) {
            double distance = mode == Mode.ROTATE
                    ? ringDistance(view, at, axes[axis], perPixel * EditorScale.of(RING_RADIUS), mouseX, mouseY)
                    : segmentDistance(view, at, at.add(axes[axis].mul(perPixel * EditorScale.of(ARROW_LENGTH))), mouseX, mouseY);
            if (distance < closest) {
                closest = distance;
                best = axis;
            }
        }
        return best;
    }

    private static Vector3[] plane(Vector3 axis) {
        Vector3 helper = Math.abs(axis.y()) < 0.9 ? new Vector3(0, 1, 0) : new Vector3(1, 0, 0);
        Vector3 u = axis.cross(helper).normalize();
        return new Vector3[] {u, axis.cross(u).normalize()};
    }

    private static double ringDistance(SceneView view, Vector3 at, Vector3 axis, double radius, float mouseX, float mouseY) {
        Vector3[] basis = plane(axis);
        double best = Double.MAX_VALUE;
        float[] previous = null;
        for (int n = 0; n <= SEGMENTS; n++) {
            double t = Math.PI * 2 * n / SEGMENTS;
            float[] point = view.toScreen(at.add(basis[0].mul(Math.cos(t) * radius)).add(basis[1].mul(Math.sin(t) * radius)));
            if (point != null && previous != null) best = Math.min(best, distance(previous, point, mouseX, mouseY));
            previous = point;
        }
        return best;
    }

    private static double segmentDistance(SceneView view, Vector3 from, Vector3 to, float mouseX, float mouseY) {
        float[] a = view.toScreen(from);
        float[] b = view.toScreen(to);
        if (a == null || b == null) return Double.MAX_VALUE;
        return distance(a, b, mouseX, mouseY);
    }

    private static double distance(float[] a, float[] b, float x, float y) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double length = dx * dx + dy * dy;
        double t = length < 1e-6 ? 0 : Math.clamp(((x - a[0]) * dx + (y - a[1]) * dy) / length, 0, 1);
        return Math.hypot(a[0] + dx * t - x, a[1] + dy * t - y);
    }

    private static void ring(ImDrawList draw, SceneView view, Vector3 at, Vector3 axis, Vector3 toCamera, double radius, int index, boolean active) {
        Vector3[] basis = plane(axis);
        int color = Paint.axis(index);
        float thickness = EditorScale.of(active ? 3.0f : 2.0f);
        float[] previous = null;
        for (int n = 0; n <= SEGMENTS; n++) {
            double t = Math.PI * 2 * n / SEGMENTS;
            Vector3 offset = basis[0].mul(Math.cos(t)).add(basis[1].mul(Math.sin(t)));
            float[] point = view.toScreen(at.add(offset.mul(radius)));
            if (point != null && previous != null) {
                boolean front = offset.dot(toCamera) > -0.05;
                int shade = active ? EditorStyle.lighten(color, 0.25f) : front ? color : EditorStyle.withAlpha(color, 0.28f);
                draw.addLine(previous[0], previous[1], point[0], point[1], shade, front || active ? thickness : EditorScale.of(1.2f));
            }
            previous = point;
        }
    }

    private static void arrow(ImDrawList draw, SceneView view, Vector3 at, Vector3 axis, double length, int index, boolean active, float[] centre) {
        float[] tip = view.toScreen(at.add(axis.mul(length)));
        if (tip == null) return;
        int color = active ? EditorStyle.lighten(Paint.axis(index), 0.25f) : Paint.axis(index);
        draw.addLine(centre[0], centre[1], tip[0], tip[1], color, EditorScale.of(active ? 3.0f : 2.0f));
        draw.addCircleFilled(tip[0], tip[1], EditorScale.of(active ? 5.5f : 4.5f), color);
    }
}
