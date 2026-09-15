package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import org.jspecify.annotations.Nullable;

final class FaceHandles {

    private static final float HANDLE_RADIUS = 7.0f;
    private static final float HIT_RADIUS = 11.0f;
    private static final float STEM = 0.45f;
    private static final int[] AXIS_COLORS = {EditorStyle.COLOR_AXIS_X, EditorStyle.COLOR_AXIS_Y, EditorStyle.COLOR_AXIS_Z};

    private int dragging = -1;
    private int dragged;
    private @Nullable CFrame startFrame;
    private @Nullable Vector3 startSize;
    private double startAlong;

    boolean active() {
        return dragging >= 0;
    }

    boolean render(ImDrawList draw, SceneView view, SceneDocument document, Part part, boolean hovered, boolean snap, float step) {
        CFrame world = Transforms.world(part);
        Vector3 mouse = new Vector3(ImGui.getMousePosX(), ImGui.getMousePosY(), 0);
        int over = -1;
        double closest = EditorScale.of(HIT_RADIUS);
        float[][] screens = new float[6][];
        for (int face = 0; face < 6; face++) {
            Vector3 center = faceCenter(part, world, face);
            Vector3 stem = center.add(faceDirection(world, face).mul(STEM));
            float[] a = view.toScreen(center);
            float[] b = view.toScreen(stem);
            if (a == null || b == null) continue;
            screens[face] = b;
            draw.addLine(a[0], a[1], b[0], b[1], AXIS_COLORS[face / 2], 2.0f);
            double distance = Math.hypot(b[0] - mouse.x(), b[1] - mouse.y());
            if (distance <= closest) {
                closest = distance;
                over = face;
            }
        }
        if (dragging >= 0 && dragged != part.id()) dragging = -1;
        int highlighted = dragging >= 0 ? dragging : hovered ? over : -1;
        for (int face = 0; face < 6; face++) {
            if (screens[face] == null) continue;
            int color = face == highlighted ? EditorStyle.lighten(AXIS_COLORS[face / 2], 0.35f) : AXIS_COLORS[face / 2];
            draw.addCircleFilled(screens[face][0], screens[face][1], EditorScale.of(HANDLE_RADIUS), color);
        }
        if (dragging < 0 && hovered && over >= 0 && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            dragging = over;
            dragged = part.id();
            startFrame = world;
            startSize = part.size;
            startAlong = along(view, faceCenter(part, world, over), faceDirection(world, over));
        }
        if (dragging < 0) return over >= 0 && hovered;
        if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            dragging = -1;
            return true;
        }
        resize(view, document, part, snap, step);
        return true;
    }

    private void resize(SceneView view, SceneDocument document, Part part, boolean snap, float step) {
        int axis = dragging / 2;
        Vector3 direction = faceDirection(startFrame, dragging);
        Vector3 origin = faceCenter(startSize, startFrame, dragging);
        double delta = along(view, origin, direction) - startAlong;
        boolean symmetric = ImGui.getIO().getKeyShift();
        double grow = symmetric ? delta * 2.0 : delta;
        double startLength = component(startSize, axis);
        double length = Math.max(snap ? step : 0.05, snap ? Rays.snap(startLength + grow, step) : startLength + grow);
        double applied = length - startLength;
        Vector3 size = withComponent(startSize, axis, length);
        CFrame frame = symmetric ? startFrame : startFrame.withPosition(startFrame.position().add(direction.mul(applied * 0.5)));
        document.history().execute(new Batch("Resize", Frames.worldWrites(document, part, frame, size)));
    }

    private static double along(SceneView view, Vector3 lineOrigin, Vector3 lineDirection) {
        Vector3 direction = view.rayDirection(ImGui.getMousePosX(), ImGui.getMousePosY());
        return Rays.alongLine(view.cameraPosition(), direction, lineOrigin, lineDirection);
    }

    private static Vector3 faceCenter(Part part, CFrame world, int face) {
        return faceCenter(part.size, world, face);
    }

    private static Vector3 faceCenter(Vector3 size, CFrame world, int face) {
        int axis = face / 2;
        double sign = face % 2 == 0 ? 1 : -1;
        Vector3 local = withComponent(Vector3.ZERO, axis, sign * component(size, axis) * 0.5);
        return world.pointToWorld(local);
    }

    private static Vector3 faceDirection(CFrame world, int face) {
        double sign = face % 2 == 0 ? 1 : -1;
        Vector3 local = withComponent(Vector3.ZERO, face / 2, sign);
        return world.vectorToWorld(local).normalize();
    }

    private static double component(Vector3 vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x();
            case 1 -> vector.y();
            default -> vector.z();
        };
    }

    private static Vector3 withComponent(Vector3 vector, int axis, double value) {
        return switch (axis) {
            case 0 -> new Vector3(value, vector.y(), vector.z());
            case 1 -> new Vector3(vector.x(), value, vector.z());
            default -> new Vector3(vector.x(), vector.y(), value);
        };
    }
}
