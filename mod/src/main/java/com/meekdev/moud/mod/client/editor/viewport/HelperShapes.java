package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.audio.Sound;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.interp.PathCurve;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Color;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.SpawnLocation;
import com.meekdev.moud.core.render.Camera;
import com.meekdev.moud.core.render.CameraPath;
import com.meekdev.moud.core.render.LightSource;
import com.meekdev.moud.core.render.SpotLight;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.core.zone.Zone;
import com.meekdev.moud.core.zone.ZoneShape;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import java.util.ArrayList;
import java.util.List;

final class HelperShapes {

    private static final int SEGMENTS = 40;
    private static final int ZONE = EditorStyle.rgba(110, 230, 140, 150);
    private static final int ZONE_SELECTED = EditorStyle.rgba(140, 255, 170, 230);
    private static final int SOUND = EditorStyle.rgba(120, 180, 255, 170);
    private static final int CAMERA = EditorStyle.rgba(250, 200, 90, 150);
    private static final int CAMERA_SELECTED = EditorStyle.rgba(255, 225, 130, 240);
    private static final int SPAWN = EditorStyle.rgba(120, 220, 255, 170);
    private static final int SPAWN_SELECTED = EditorStyle.rgba(170, 240, 255, 240);
    private static final double CONE_DEPTH = 1.6;
    private static final double ASPECT = 16.0 / 9.0;
    private static final int PATH_SAMPLES = 80;

    private HelperShapes() {}

    static void draw(ImDrawList draw, SceneView view, SceneDocument document) {
        Instance world = document.world();
        if (world == null) return;
        List<Instance> all = new ArrayList<>();
        collect(world, all);
        for (Instance instance : all) {
            if (!document.editable(instance)) continue;
            boolean selected = document.selection().isSelected(instance.id());
            switch (instance) {
                case Zone zone -> zone(draw, view, zone, selected ? ZONE_SELECTED : ZONE);
                case SpotLight spot -> spot(draw, view, spot, colour(spot, selected));
                case LightSource light -> sphere(draw, view, Transforms.world(light), light.range, colour(light, selected));
                case Sound sound when selected -> sound(draw, view, sound);
                case Camera shot -> camera(draw, view, shot, selected ? CAMERA_SELECTED : CAMERA);
                case CameraPath path -> path(draw, view, path, selected ? CAMERA_SELECTED : CAMERA);
                case SpawnLocation spawn -> spawn(draw, view, spawn, selected ? SPAWN_SELECTED : SPAWN);
                default -> { }
            }
        }
    }

    private static void collect(Instance at, List<Instance> into) {
        for (Instance child : at.children()) {
            if (child instanceof ViewportFrame) continue;
            into.add(child);
            collect(child, into);
        }
    }

    private static int colour(LightSource light, boolean selected) {
        Color c = light.color;
        return EditorStyle.rgba(Math.round(c.r() * 255), Math.round(c.g() * 255), Math.round(c.b() * 255), selected ? 220 : 90);
    }

    private static void zone(ImDrawList draw, SceneView view, Zone zone, int colour) {
        CFrame frame = Transforms.world(zone);
        Vector3 half = zone.size.mul(0.5);
        if (zone.shape == ZoneShape.SPHERE) {
            sphere(draw, view, frame, half.x(), colour);
            return;
        }
        if (zone.shape == ZoneShape.CYLINDER) {
            ring(draw, view, frame.mul(CFrame.at(0, half.y(), 0)), half.x(), 0, colour);
            ring(draw, view, frame.mul(CFrame.at(0, -half.y(), 0)), half.x(), 0, colour);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI * 0.5;
                Vector3 side = new Vector3(Math.cos(a) * half.x(), 0, Math.sin(a) * half.x());
                line(draw, view, frame.pointToWorld(side.add(new Vector3(0, half.y(), 0))), frame.pointToWorld(side.sub(new Vector3(0, half.y(), 0))), colour);
            }
            return;
        }
        Vector3[] corners = new Vector3[8];
        for (int n = 0; n < 8; n++) {
            corners[n] = frame.pointToWorld(new Vector3((n & 1) == 0 ? -half.x() : half.x(), (n & 2) == 0 ? -half.y() : half.y(), (n & 4) == 0 ? -half.z() : half.z()));
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : edges) line(draw, view, corners[edge[0]], corners[edge[1]], colour);
    }

    private static void spot(ImDrawList draw, SceneView view, SpotLight spot, int colour) {
        CFrame frame = Transforms.world(spot);
        double radius = Math.tan(Math.toRadians(spot.outerAngle)) * spot.range;
        CFrame end = frame.mul(CFrame.at(0, 0, -spot.range));
        ring(draw, view, end, radius, 1, colour);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI * 0.5;
            line(draw, view, frame.position(), end.pointToWorld(new Vector3(Math.cos(a) * radius, Math.sin(a) * radius, 0)), colour);
        }
    }

    private static void camera(ImDrawList draw, SceneView view, Camera shot, int colour) {
        CFrame frame = Transforms.world(shot);
        double fov = shot.fov > 0 ? shot.fov : 70;
        double tall = Math.tan(Math.toRadians(fov) * 0.5) * CONE_DEPTH;
        double wide = tall * ASPECT;
        Vector3 eye = frame.position();
        Vector3[] corners = {
                frame.pointToWorld(new Vector3(-wide, tall, -CONE_DEPTH)), frame.pointToWorld(new Vector3(wide, tall, -CONE_DEPTH)),
                frame.pointToWorld(new Vector3(wide, -tall, -CONE_DEPTH)), frame.pointToWorld(new Vector3(-wide, -tall, -CONE_DEPTH))};
        for (int n = 0; n < 4; n++) {
            line(draw, view, eye, corners[n], colour);
            line(draw, view, corners[n], corners[(n + 1) % 4], colour);
        }
        line(draw, view, frame.pointToWorld(new Vector3(-wide * 0.4, tall * 1.15, -CONE_DEPTH)), frame.pointToWorld(new Vector3(0, tall * 1.5, -CONE_DEPTH)), colour);
        line(draw, view, frame.pointToWorld(new Vector3(0, tall * 1.5, -CONE_DEPTH)), frame.pointToWorld(new Vector3(wide * 0.4, tall * 1.15, -CONE_DEPTH)), colour);
    }

    private static void spawn(ImDrawList draw, SceneView view, SpawnLocation spawn, int colour) {
        CFrame top = Transforms.world(spawn).mul(CFrame.at(0, spawn.size.y() * 0.5 + 0.05, 0));
        double length = Math.max(1, Math.min(spawn.size.x(), spawn.size.z()) * 0.4);
        Vector3 tail = top.pointToWorld(new Vector3(0, 0, length * 0.5));
        Vector3 tip = top.pointToWorld(new Vector3(0, 0, -length * 0.5));
        line(draw, view, tail, tip, colour);
        line(draw, view, tip, top.pointToWorld(new Vector3(-length * 0.25, 0, -length * 0.25)), colour);
        line(draw, view, tip, top.pointToWorld(new Vector3(length * 0.25, 0, -length * 0.25)), colour);
        if (!spawn.enabled) {
            float[] at = view.toScreen(top.position());
            if (at != null) draw.addText(at[0] + 6, at[1] - 6, colour, "off");
        }
    }

    private static void path(ImDrawList draw, SceneView view, CameraPath path, int colour) {
        List<CFrame> points = path.points();
        for (int n = 0; n < points.size(); n++) {
            float[] at = view.toScreen(points.get(n).position());
            if (at == null) continue;
            draw.addCircleFilled(at[0], at[1], 4f, colour);
            draw.addText(at[0] + 6, at[1] - 14, colour, String.valueOf(n + 1));
        }
        if (points.size() < 2) return;
        PathCurve curve = new PathCurve(points, path.closed);
        Vector3 previous = curve.sample(0).position();
        for (int n = 1; n <= PATH_SAMPLES; n++) {
            Vector3 next = curve.sample(n / (double) PATH_SAMPLES).position();
            line(draw, view, previous, next, colour);
            previous = next;
        }
    }

    private static void sound(ImDrawList draw, SceneView view, Sound sound) {
        Instance anchor = sound.parent();
        while (anchor != null && !(anchor instanceof Spatial)) anchor = anchor.parent();
        if (anchor == null) return;
        CFrame frame = CFrame.at(Transforms.world(anchor).position());
        ring(draw, view, frame, sound.minDistance, 0, SOUND);
        ring(draw, view, frame, sound.maxDistance, 0, EditorStyle.withAlpha(SOUND, 0.4f));
    }

    private static void sphere(ImDrawList draw, SceneView view, CFrame frame, double radius, int colour) {
        CFrame centre = CFrame.at(frame.position());
        ring(draw, view, centre, radius, 0, colour);
        ring(draw, view, centre, radius, 1, colour);
        ring(draw, view, centre, radius, 2, colour);
    }

    private static void ring(ImDrawList draw, SceneView view, CFrame frame, double radius, int plane, int colour) {
        Vector3 previous = null;
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = i * Math.PI * 2 / SEGMENTS;
            double c = Math.cos(a) * radius;
            double s = Math.sin(a) * radius;
            Vector3 local = switch (plane) {
                case 0 -> new Vector3(c, 0, s);
                case 1 -> new Vector3(c, s, 0);
                default -> new Vector3(0, c, s);
            };
            Vector3 point = frame.pointToWorld(local);
            if (previous != null) line(draw, view, previous, point, colour);
            previous = point;
        }
    }

    private static void line(ImDrawList draw, SceneView view, Vector3 from, Vector3 to, int colour) {
        float[] a = view.toScreen(from);
        float[] b = view.toScreen(to);
        if (a == null || b == null) return;
        draw.addLine(a[0], a[1], b[0], b[1], colour, 1.5f);
    }
}
