package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class OrientationCube {

    private static final float SIZE = 34.0f;
    private static final float MARGIN = 14.0f;
    private static final String[] LABELS = {"East", "West", "Top", "Bottom", "South", "North"};
    private static final int[][] AXES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int FACE = EditorStyle.surface(1.25f);
    private static final int FACE_HOVER = EditorStyle.COLOR_WIDGET_HOVER;
    private static final int EDGE = EditorStyle.withAlpha(EditorStyle.COLOR_TEXT, 0.35f);

    private record Face(int index, float[] corners, double depth) {}

    boolean render(ImDrawList draw, float right, float top, EditorCamera camera) {
        float half = EditorScale.of(SIZE);
        float cx = right - EditorScale.of(MARGIN) - half * 1.5f;
        float cy = top + EditorScale.of(MARGIN) + half * 1.5f;
        double yaw = Math.toRadians(camera.yaw());
        double pitch = Math.toRadians(camera.pitch());
        double[] forward = {-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
        double[] side = {-Math.cos(yaw), 0, -Math.sin(yaw)};
        double[] up = cross(side, forward);

        List<Face> faces = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int[] n = AXES[i];
            double facing = n[0] * forward[0] + n[1] * forward[1] + n[2] * forward[2];
            if (facing > -0.05) continue;
            int[] a = n[0] != 0 ? new int[] {0, 1, 0} : new int[] {1, 0, 0};
            int[] b = n[2] != 0 ? new int[] {0, 1, 0} : new int[] {0, 0, 1};
            if (n[1] != 0) b = new int[] {0, 0, 1};
            float[] corners = new float[8];
            int[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int c = 0; c < 4; c++) {
                double x = n[0] + a[0] * signs[c][0] + b[0] * signs[c][1];
                double y = n[1] + a[1] * signs[c][0] + b[1] * signs[c][1];
                double z = n[2] + a[2] * signs[c][0] + b[2] * signs[c][1];
                corners[c * 2] = cx + (float) (x * side[0] + y * side[1] + z * side[2]) * half;
                corners[c * 2 + 1] = cy - (float) (x * up[0] + y * up[1] + z * up[2]) * half;
            }
            faces.add(new Face(i, corners, facing));
        }
        faces.sort(Comparator.comparingDouble(Face::depth).reversed());

        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        Face over = null;
        for (Face face : faces) {
            if (inside(face.corners(), mouseX, mouseY)) over = face;
        }
        axis(draw, cx, cy, half, side, up, 0, EditorStyle.COLOR_AXIS_X);
        axis(draw, cx, cy, half, side, up, 1, EditorStyle.COLOR_AXIS_Y);
        axis(draw, cx, cy, half, side, up, 2, EditorStyle.COLOR_AXIS_Z);
        for (Face face : faces) {
            float[] p = face.corners();
            draw.addQuadFilled(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], face == over ? FACE_HOVER : FACE);
            draw.addQuad(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], EDGE, 1.0f);
            float lx = (p[0] + p[2] + p[4] + p[6]) * 0.25f;
            float ly = (p[1] + p[3] + p[5] + p[7]) * 0.25f;
            float area = Math.abs((p[4] - p[0]) * (p[7] - p[3]) - (p[6] - p[2]) * (p[5] - p[1])) * 0.5f;
            if (area > half * half * 0.9f) {
                String label = LABELS[face.index()];
                float width = ImGui.calcTextSize(label).x;
                draw.addText(lx - width * 0.5f, ly - ImGui.getTextLineHeight() * 0.5f, EditorStyle.COLOR_TEXT, label);
            }
        }

        float buttonTop = cy + half * 1.75f;
        String mode = camera.orthographic() ? "Orthographic" : "Perspective";
        float modeWidth = ImGui.calcTextSize(mode).x;
        boolean overMode = mouseX >= cx - modeWidth * 0.5f - 4 && mouseX <= cx + modeWidth * 0.5f + 4
                && mouseY >= buttonTop && mouseY <= buttonTop + ImGui.getTextLineHeight() + 4;
        draw.addRectFilled(cx - modeWidth * 0.5f - 4, buttonTop, cx + modeWidth * 0.5f + 4, buttonTop + ImGui.getTextLineHeight() + 4,
                overMode ? FACE_HOVER : EditorStyle.withAlpha(FACE, 0.85f), 3.0f);
        draw.addText(cx - modeWidth * 0.5f, buttonTop + 2, EditorStyle.COLOR_TEXT, mode);

        boolean hovered = over != null || overMode;
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            if (over != null) {
                int[] n = AXES[over.index()];
                camera.alignTo(-n[0], -n[1], -n[2]);
            } else if (overMode) {
                camera.toggleOrthographic();
            }
        }
        if (over != null) ImGui.setTooltip("Look from the " + LABELS[over.index()].toLowerCase());
        else if (overMode) ImGui.setTooltip("Switch between perspective and orthographic");
        return hovered;
    }

    private static void axis(ImDrawList draw, float cx, float cy, float half, double[] side, double[] up, int axis, int color) {
        double[] v = new double[3];
        v[axis] = 1.6;
        float x = cx + (float) (v[0] * side[0] + v[1] * side[1] + v[2] * side[2]) * half;
        float y = cy - (float) (v[0] * up[0] + v[1] * up[1] + v[2] * up[2]) * half;
        draw.addLine(cx, cy, x, y, color, 2.0f);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static boolean inside(float[] p, float x, float y) {
        boolean positive = false;
        boolean negative = false;
        for (int i = 0; i < 4; i++) {
            float ax = p[i * 2], ay = p[i * 2 + 1];
            float bx = p[(i + 1) % 4 * 2], by = p[(i + 1) % 4 * 2 + 1];
            float cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax);
            if (cross > 0) positive = true;
            if (cross < 0) negative = true;
        }
        return !(positive && negative);
    }
}
