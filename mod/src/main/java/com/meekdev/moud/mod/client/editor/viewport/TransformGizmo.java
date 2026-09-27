package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Quat;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.core.zone.Zone;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.document.SetProperty;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.extension.imguizmo.flag.Mode;
import imgui.flag.ImGuiKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class TransformGizmo {

    private static final float GIZMO_SIZE_CLIP_SPACE = 0.14f;
    private static final float SNAP_RADIUS = 18.0f;
    private static final double SMALLEST_SIZE = 0.01;
    private static final int[] DIGIT_KEYS = {ImGuiKey._0, ImGuiKey._1, ImGuiKey._2, ImGuiKey._3, ImGuiKey._4, ImGuiKey._5, ImGuiKey._6, ImGuiKey._7, ImGuiKey._8, ImGuiKey._9};
    private static final int[] PAD_KEYS = {ImGuiKey.Keypad0, ImGuiKey.Keypad1, ImGuiKey.Keypad2, ImGuiKey.Keypad3, ImGuiKey.Keypad4, ImGuiKey.Keypad5, ImGuiKey.Keypad6, ImGuiKey.Keypad7, ImGuiKey.Keypad8, ImGuiKey.Keypad9};

    private record Start(CFrame world, Vector3 origin, @Nullable Vector3 size) {}

    private final SceneDocument document;
    private final GizmoState state;
    private final FaceHandles faceHandles = new FaceHandles();
    private final float[] model = new float[16];
    private final float[] snap = new float[3];
    private final Map<Integer, Start> starts = new LinkedHashMap<>();
    private final List<Integer> moving = new ArrayList<>();
    private final StringBuilder typed = new StringBuilder();
    private @Nullable Matrix4f gizmoStart;
    private Vector3 pivot = Vector3.ZERO;
    private Quat basis = Quat.IDENTITY;
    private @Nullable Vector3 snapOffset;
    private @Nullable Vector3 snapTarget;

    TransformGizmo(SceneDocument document, GizmoState state) {
        this.document = document;
        this.state = state;
    }

    void reset() {
        starts.clear();
        moving.clear();
        gizmoStart = null;
        typed.setLength(0);
        snapOffset = null;
    }

    boolean dragging() {
        return gizmoStart != null;
    }

    boolean render(ImDrawList draw, SceneView view, Vector3 camera, boolean orthographic, boolean hovered, boolean snapping) {
        Integer operation = state.operation().orElse(null);
        List<Instance> roots = Manipulate.spatialRoots(document);
        Instance leader = active(roots);
        if (operation == null || leader == null) {
            reset();
            return false;
        }
        boolean groupScale = state.tool() == GizmoState.Tool.SCALE && (roots.size() > 1 || !(leader instanceof Part));
        if (state.tool() == GizmoState.Tool.SCALE && !groupScale && leader instanceof Part part) {
            return faceHandles.render(draw, view, document, part, hovered, snapping, state.gridStep());
        }
        ImGuizmo.setOrthographic(orthographic);
        ImGuizmo.setDrawList();
        ImGuizmo.setGizmoSizeClipSpace(GIZMO_SIZE_CLIP_SPACE);
        ImGuizmo.setRect(view.originX(), view.originY(), view.width(), view.height());

        if (gizmoStart == null || state.tool() == GizmoState.Tool.PIVOT) handle(leader, roots, camera, groupScale).get(model);
        int mode = state.tool() == GizmoState.Tool.PIVOT || state.space() != GizmoState.Space.WORLD || groupScale ? Mode.LOCAL : Mode.WORLD;
        if (snapping) {
            float step = state.snapStep();
            snap[0] = step;
            snap[1] = step;
            snap[2] = step;
            ImGuizmo.manipulate(view.view, view.projection, operation, mode, model, null, snap);
        } else {
            ImGuizmo.manipulate(view.view, view.projection, operation, mode, model);
        }
        if (state.tool() == GizmoState.Tool.PIVOT) drawPivot(draw, view, leader);
        if (!ImGuizmo.isUsing()) {
            if (gizmoStart != null) finish();
            return ImGuizmo.isOver();
        }
        if (gizmoStart == null) begin(leader, roots, camera, groupScale);
        Matrix4f moved = new Matrix4f().set(model);
        readTyping();
        if (state.tool() == GizmoState.Tool.PIVOT) {
            movePivot(leader, moved, camera);
            return true;
        }
        if (state.tool() == GizmoState.Tool.TRANSLATE) moved = vertexSnap(draw, view, leader, moved, camera);
        moved = typedValue(draw, moved);
        apply(moved, groupScale);
        return true;
    }

    private @Nullable Instance active(List<Instance> roots) {
        Instance primary = document.primary();
        if (primary != null && roots.contains(primary)) return primary;
        return roots.isEmpty() ? null : roots.getLast();
    }

    private Matrix4f handle(Instance leader, List<Instance> roots, Vector3 camera, boolean groupScale) {
        Vector3 at;
        Quat turn;
        if (state.tool() == GizmoState.Tool.PIVOT) {
            at = Manipulate.origin(leader);
            turn = Transforms.world(leader).rotation();
        } else {
            at = roots.size() == 1 || state.pivot() == GizmoState.Pivot.ACTIVE ? Manipulate.origin(leader) : Manipulate.centre(roots);
            turn = basis(leader, groupScale);
        }
        Vector3 local = at.sub(camera);
        return new Matrix4f().translation((float) local.x(), (float) local.y(), (float) local.z()).rotate(quaternion(turn));
    }

    private Quat basis(Instance leader, boolean groupScale) {
        return switch (state.space()) {
            case WORLD -> groupScale ? Quat.IDENTITY : Transforms.world(leader).rotation();
            case LOCAL -> Transforms.world(leader).rotation();
            case PARENT -> leader.parent() instanceof Spatial ? Transforms.world(leader.parent()).rotation() : Quat.IDENTITY;
        };
    }

    private void begin(Instance leader, List<Instance> roots, Vector3 camera, boolean groupScale) {
        gizmoStart = new Matrix4f().set(model);
        typed.setLength(0);
        starts.clear();
        moving.clear();
        Vector3f at = gizmoStart.getTranslation(new Vector3f());
        pivot = new Vector3(at.x + camera.x(), at.y + camera.y(), at.z + camera.z());
        basis = basis(leader, groupScale);
        if (state.tool() == GizmoState.Tool.PIVOT) {
            hold(leader);
        } else {
            for (Instance root : roots) {
                moving.add(root.id());
                hold(root);
                if (groupScale) holdBelow(root);
            }
        }
        snapOffset = null;
        if (state.tool() != GizmoState.Tool.PIVOT && ImGui.getIO().getKeyShift()) {
            document.pasteTransformed(document.selectedRoots(), frame -> frame, "Duplicate", false);
        }
    }

    private void hold(Instance instance) {
        starts.put(instance.id(), new Start(Transforms.world(instance), Manipulate.origin(instance), size(instance)));
    }

    private void holdBelow(Instance root) {
        for (Instance child : root.children()) {
            if (child instanceof Spatial && document.pickable(child)) hold(child);
            holdBelow(child);
        }
    }

    private void finish() {
        reset();
    }

    private void apply(Matrix4f moved, boolean groupScale) {
        List<Edit> edits = new ArrayList<>();
        boolean individual = state.pivot() == GizmoState.Pivot.EACH;
        if (groupScale) {
            Vector3f scale = moved.getScale(new Vector3f());
            Vector3 factor = new Vector3(Math.max(0.01, scale.x), Math.max(0.01, scale.y), Math.max(0.01, scale.z));
            for (int id : moving) {
                Instance instance = document.find(id);
                Start start = starts.get(id);
                if (instance == null || start == null) continue;
                Vector3 about = individual ? start.origin() : pivot;
                CFrame parent = instance.parent() == null ? CFrame.IDENTITY : Transforms.world(instance.parent());
                CFrame world = scaled(start, about, factor);
                edits.addAll(scaleWrites(instance, parent, world, start, factor));
                scaleBelow(instance, world, about, factor, edits);
            }
            document.history().execute(new Batch(named("Scale"), edits));
            return;
        }
        Matrix4f delta = new Matrix4f(moved).mul(new Matrix4f(gizmoStart).invert());
        Quaternionf q = delta.getNormalizedRotation(new Quaternionf());
        Quat turn = new Quat(q.x, q.y, q.z, q.w);
        Vector3f from = gizmoStart.getTranslation(new Vector3f());
        Vector3f to = delta.transformPosition(new Vector3f(from));
        Vector3 shift = new Vector3(to.x - from.x, to.y - from.y, to.z - from.z);
        boolean each = individual && state.tool() == GizmoState.Tool.ROTATE;
        for (int id : moving) {
            Instance instance = document.find(id);
            Start start = starts.get(id);
            if (instance == null || start == null) continue;
            Vector3 about = each ? start.origin() : pivot;
            Vector3 position = about.add(turn.rotate(start.world().position().sub(about))).add(shift);
            CFrame world = new CFrame(position, turn.mul(start.world().rotation()).normalize());
            edits.addAll(Frames.worldWrites(document, instance, world, null));
        }
        document.history().execute(new Batch(named(state.tool() == GizmoState.Tool.ROTATE ? "Rotate" : "Move"), edits));
    }

    private String named(String verb) {
        Instance only = moving.size() == 1 ? document.find(moving.getFirst()) : null;
        return only == null ? verb : verb + " " + only.name();
    }

    private void scaleBelow(Instance at, CFrame world, Vector3 about, Vector3 factor, List<Edit> edits) {
        for (Instance child : at.children()) {
            CFrame childWorld = world.mul(Transforms.local(child));
            Start start = starts.get(child.id());
            if (start != null) {
                childWorld = scaled(start, about, factor);
                edits.addAll(scaleWrites(child, world, childWorld, start, factor));
            }
            scaleBelow(child, childWorld, about, factor, edits);
        }
    }

    private CFrame scaled(Start start, Vector3 about, Vector3 factor) {
        Vector3 offset = basis.inverse().rotate(start.world().position().sub(about));
        return start.world().withPosition(about.add(basis.rotate(offset.mul(factor))));
    }

    private List<Edit> scaleWrites(Instance instance, CFrame parent, CFrame world, Start start, Vector3 factor) {
        CFrame local = parent.inverse().mul(world);
        Vector3 pivotAt = ((Spatial) instance).pivot;
        if (!pivotAt.equals(Vector3.ZERO)) local = local.mul(CFrame.at(pivotAt));
        SetProperty frame = new SetProperty(document.ref(instance.id()), instance.def().property("cframe").index(), local, "Transform");
        if (start.size() == null) return List.of(frame);
        Quat toBasis = basis.inverse().mul(start.world().rotation());
        Vector3 stretch = new Vector3(
                toBasis.rotate(Vector3.RIGHT).mul(factor).length(),
                toBasis.rotate(Vector3.UP).mul(factor).length(),
                toBasis.rotate(new Vector3(0, 0, 1)).mul(factor).length());
        Vector3 size = start.size().mul(stretch);
        Vector3 clamped = new Vector3(Math.max(SMALLEST_SIZE, size.x()), Math.max(SMALLEST_SIZE, size.y()), Math.max(SMALLEST_SIZE, size.z()));
        return List.of(frame, new SetProperty(document.ref(instance.id()), instance.def().property("size").index(), clamped, "Transform"));
    }

    private static @Nullable Vector3 size(Instance instance) {
        if (instance instanceof Part part) return part.size;
        if (instance instanceof Zone zone) return zone.size;
        return null;
    }

    private void movePivot(Instance leader, Matrix4f moved, Vector3 camera) {
        Start start = starts.get(leader.id());
        if (start == null) return;
        Vector3f at = moved.getTranslation(new Vector3f());
        Vector3 world = new Vector3(at.x + camera.x(), at.y + camera.y(), at.z + camera.z());
        Vector3 offset = start.world().pointToObject(world);
        CFrame local = Transforms.localFor(leader, start.world()).mul(CFrame.at(offset));
        List<Edit> edits = List.of(
                new SetProperty(document.ref(leader.id()), leader.def().property("pivot").index(), offset, "Pivot"),
                new SetProperty(document.ref(leader.id()), leader.def().property("cframe").index(), local, "Pivot"));
        document.history().execute(new Batch("Move pivot", edits));
    }

    private void drawPivot(ImDrawList draw, SceneView view, Instance leader) {
        CFrame world = Transforms.world(leader);
        float[] at = view.toScreen(world.pointToWorld(((Spatial) leader).pivot));
        if (at == null) return;
        float r = EditorScale.of(6.0f);
        draw.addQuadFilled(at[0], at[1] - r, at[0] + r, at[1], at[0], at[1] + r, at[0] - r, at[1], EditorStyle.COLOR_HIGHLIGHT);
    }

    private Matrix4f vertexSnap(ImDrawList draw, SceneView view, Instance leader, Matrix4f moved, Vector3 camera) {
        if (!ImGui.isKeyDown(ImGuiKey.V)) {
            snapOffset = null;
            return moved;
        }
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        if (snapOffset == null && leader instanceof Part part) {
            Vector3 nearest = nearestPoint(view, List.of(part), mouseX, mouseY, Double.MAX_VALUE);
            Vector3f at = moved.getTranslation(new Vector3f());
            snapOffset = nearest == null ? Vector3.ZERO : nearest.sub(new Vector3(at.x + camera.x(), at.y + camera.y(), at.z + camera.z()));
        }
        List<Part> others = new ArrayList<>();
        Instance world = document.world();
        if (world != null) collectParts(world, others);
        others.removeIf(part -> starts.containsKey(part.id()) || document.selection().isSelected(part.id()));
        Vector3 target = nearestPoint(view, others, mouseX, mouseY, EditorScale.of(SNAP_RADIUS));
        snapTarget = target;
        if (target == null) return moved;
        float[] mark = view.toScreen(target);
        if (mark != null) draw.addCircle(mark[0], mark[1], EditorScale.of(7.0f), EditorStyle.COLOR_HIGHLIGHT, 16, 2.0f);
        Vector3 centre = target.sub(snapOffset == null ? Vector3.ZERO : snapOffset).sub(camera);
        return new Matrix4f(moved).setTranslation((float) centre.x(), (float) centre.y(), (float) centre.z());
    }

    private void collectParts(Instance at, List<Part> into) {
        for (Instance child : at.children()) {
            if (child instanceof ViewportFrame) continue;
            if (child instanceof Part part && part.visible) into.add(part);
            collectParts(child, into);
        }
    }

    private static @Nullable Vector3 nearestPoint(SceneView view, List<Part> parts, float mouseX, float mouseY, double radius) {
        Vector3 best = null;
        double bestDistance = radius * radius;
        for (Part part : parts) {
            CFrame world = Transforms.world(part);
            Vector3 half = part.size.mul(0.5);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Vector3 point = world.pointToWorld(new Vector3(half.x() * x, half.y() * y, half.z() * z));
                        float[] screen = view.toScreen(point);
                        if (screen == null) continue;
                        double dx = screen[0] - mouseX;
                        double dy = screen[1] - mouseY;
                        double distance = dx * dx + dy * dy;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = point;
                        }
                    }
                }
            }
        }
        return best;
    }

    private void readTyping() {
        for (int n = 0; n < 10; n++) {
            if (ImGui.isKeyPressed(DIGIT_KEYS[n], false) || ImGui.isKeyPressed(PAD_KEYS[n], false)) typed.append(n);
        }
        if (ImGui.isKeyPressed(ImGuiKey.Period, false) || ImGui.isKeyPressed(ImGuiKey.KeypadDecimal, false) || ImGui.isKeyPressed(ImGuiKey.Comma, false)) {
            if (typed.indexOf(".") < 0) typed.append('.');
        }
        if (ImGui.isKeyPressed(ImGuiKey.Minus, false) || ImGui.isKeyPressed(ImGuiKey.KeypadSubtract, false)) {
            if (typed.length() > 0 && typed.charAt(0) == '-') typed.deleteCharAt(0);
            else typed.insert(0, '-');
        }
        if (ImGui.isKeyPressed(ImGuiKey.Backspace, false) && typed.length() > 0) typed.setLength(typed.length() - 1);
    }

    private Matrix4f typedValue(ImDrawList draw, Matrix4f moved) {
        if (typed.length() == 0 || typed.toString().equals("-") || typed.toString().equals(".")) return moved;
        double value;
        try {
            value = Double.parseDouble(typed.toString());
        } catch (NumberFormatException e) {
            return moved;
        }
        Matrix4f result;
        String text;
        if (state.tool() == GizmoState.Tool.ROTATE) {
            Matrix4f delta = new Matrix4f(moved).mul(new Matrix4f(gizmoStart).invert());
            AxisAngle4f turn = new AxisAngle4f().set(delta.getNormalizedRotation(new Quaternionf()));
            Vector3f axis = new Vector3f(turn.x, turn.y, turn.z);
            if (axis.lengthSquared() < 1e-6f) axis.set(0, 1, 0);
            if (turn.angle < 0) axis.negate();
            Vector3f origin = gizmoStart.getTranslation(new Vector3f());
            result = new Matrix4f().translation(origin).rotate((float) Math.toRadians(value), axis.normalize())
                    .mul(new Matrix4f(gizmoStart).setTranslation(0, 0, 0));
            text = String.format(Locale.ROOT, "%s°", typed);
        } else {
            Vector3f from = gizmoStart.getTranslation(new Vector3f());
            Vector3f to = moved.getTranslation(new Vector3f());
            Vector3f change = to.sub(from, new Vector3f());
            Vector3f[] axes = {gizmoStart.getColumn(0, new Vector3f()).normalize(), gizmoStart.getColumn(1, new Vector3f()).normalize(), gizmoStart.getColumn(2, new Vector3f()).normalize()};
            if (state.space() == GizmoState.Space.WORLD) axes = new Vector3f[] {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};
            int major = 0;
            for (int i = 1; i < 3; i++) {
                if (Math.abs(change.dot(axes[i])) > Math.abs(change.dot(axes[major]))) major = i;
            }
            double sign = change.dot(axes[major]) < 0 ? -1 : 1;
            Vector3f offset = new Vector3f(axes[major]).mul((float) (value * sign));
            result = new Matrix4f(gizmoStart).setTranslation(from.add(offset, new Vector3f()));
            text = String.format(Locale.ROOT, "%s m along %s", typed, "XYZ".charAt(major));
        }
        float x = ImGui.getMousePosX() + EditorScale.of(16);
        float y = ImGui.getMousePosY() + EditorScale.of(12);
        float width = ImGui.calcTextSize(text).x;
        draw.addRectFilled(x - 4, y - 2, x + width + 4, y + ImGui.getTextLineHeight() + 2, EditorStyle.withAlpha(EditorStyle.COLOR_WINDOW_BACKGROUND, 0.9f), 3);
        draw.addText(x, y, EditorStyle.COLOR_TEXT, text);
        return result;
    }

    private static Quaternionf quaternion(Quat q) {
        return new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
    }
}
