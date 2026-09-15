package com.meekdev.moud.mod.client.editor.viewport;

import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.query.Queries;
import com.meekdev.moud.mod.client.editor.EditMode;
import com.meekdev.moud.mod.client.editor.document.Batch;
import com.meekdev.moud.mod.client.editor.document.Edit;
import com.meekdev.moud.mod.client.editor.document.SceneDocument;
import com.meekdev.moud.mod.client.editor.kit.Texts;
import com.meekdev.moud.mod.client.editor.kit.Toolbars;
import com.meekdev.moud.mod.client.editor.panel.Panel;
import com.meekdev.moud.mod.client.editor.style.ClassIcons;
import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class ViewportPanel implements Panel {

    public static final String ID = "viewport";

    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoBackground;
    private static final float GIZMO_SIZE_CLIP_SPACE = 0.14f;
    private static final float TOOLBAR_MARGIN_X = 6.0f;
    private static final float TOOLBAR_MARGIN_Y = 4.0f;
    private static final float RAYCAST_MAX_DISTANCE = 1024.0f;
    private static final float BILLBOARD_HALF_SIZE = 11.0f;
    private static final float BILLBOARD_CLICK_RADIUS = 14.0f;
    private static final float BILLBOARD_SHADOW_RADIUS = 14.0f;
    private static final int BILLBOARD_SHADOW_COLOR = 0x78000000;
    private static final float DRAG_THRESHOLD = 4.0f;
    private static final float OUTLINE_THICKNESS = 2.0f;
    private static final int BOX_FILL = EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, 0.08f);
    private static final int BOX_BORDER = EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, 0.7f);
    private static final int HOVER_OUTLINE = EditorStyle.withAlpha(EditorStyle.COLOR_TEXT, 0.35f);
    private static final int[][] EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private final SceneDocument document;
    private final IconWidgets icons;
    private final GizmoState gizmoState = new GizmoState();
    private final EditorCamera camera = new EditorCamera();
    private final SceneView view = new SceneView();
    private final float[] model = new float[16];
    private final float[] snap = new float[3];
    private final Map<Integer, Matrix4f> dragStart = new LinkedHashMap<>();
    private int session = -1;
    private boolean hovered;
    private boolean lookGesture;
    private boolean orbitGesture;
    private boolean boxing;
    private float pressX;
    private float pressY;
    private @Nullable Instance hoveredInstance;
    private Vector3 cameraPosition = Vector3.ZERO;

    public ViewportPanel(SceneDocument document, IconWidgets icons) {
        this.document = document;
        this.icons = icons;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Viewport";
    }

    @Override
    public int windowFlags() {
        return WINDOW_FLAGS;
    }

    @Override
    public void render() {
        placeCameraOnEntry();
        renderToolbar();
        float deltaSeconds = ImGui.getIO().getDeltaTime();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float right = left + ImGui.getContentRegionAvailX();
        float bottom = top + ImGui.getContentRegionAvailY();
        ImGui.dummy(Math.max(1.0f, right - left), Math.max(1.0f, bottom - top));
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        hovered = ImGui.isWindowHovered() && mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
        if (view.capture()) {
            cameraPosition = view.cameraPosition();
            ImDrawList drawList = ImGui.getWindowDrawList();
            drawList.pushClipRect(left, top, right, bottom, true);
            updateCamera(deltaSeconds);
            hoveredInstance = hovered && !lookGesture ? pickAt(ImGui.getMousePosX(), ImGui.getMousePosY()) : null;
            drawOutlines(drawList);
            drawBillboards(drawList);
            boolean gizmoBusy = renderGizmo();
            handlePicking(gizmoBusy, drawList);
            drawList.popClipRect();
            handleFrameShortcut();
            handleToolHotkeys();
        } else {
            Texts.muted("Waiting for the camera");
        }
        camera.apply();
    }

    private void placeCameraOnEntry() {
        if (session == EditMode.session()) return;
        session = EditMode.session();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) camera.placeAt(player.getEyePosition(), player.getYRot(), player.getXRot());
        dragStart.clear();
    }

    private void renderToolbar() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorScale.of(TOOLBAR_MARGIN_X), EditorScale.of(TOOLBAR_MARGIN_Y));
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, EditorStyle.itemSpacingX(), 0.0f);
        ImGui.beginChild("##viewport-toolbar", 0.0f, ImGui.getFrameHeight() + EditorScale.of(TOOLBAR_MARGIN_Y) * 2.0f, false);
        Toolbars.pushFlatButtons();
        renderToolButton("tool-select", EditorIcon.TOOL_SELECT, GizmoState.Tool.SELECT, "Select (Q)");
        ImGui.sameLine();
        renderToolButton("tool-move", EditorIcon.TOOL_MOVE, GizmoState.Tool.TRANSLATE, "Move (W)");
        ImGui.sameLine();
        renderToolButton("tool-rotate", EditorIcon.TOOL_ROTATE, GizmoState.Tool.ROTATE, "Rotate (R)");
        ImGui.sameLine();
        renderToolButton("tool-scale", EditorIcon.TOOL_SCALE, GizmoState.Tool.SCALE, "Scale (S)");
        Toolbars.groupSeparator();
        if (Toolbars.textButton(gizmoState.worldSpace() ? "World##toolbar-gizmo-space" : "Local##toolbar-gizmo-space")) gizmoState.toggleSpace();
        tooltip("Toggle the gizmo between world and local space (X)");
        Toolbars.groupSeparator();
        if (icons.toggleButton("toolbar-snap", EditorIcon.SNAP, EditorStyle.iconSizeToolbar(), gizmoState.snapEnabled())) gizmoState.toggleSnap();
        tooltip("Snap, hold Ctrl to invert");
        Toolbars.popFlatButtons();
        ImGui.endChild();
        ImGui.popStyleVar(2);
    }

    private void renderToolButton(String id, EditorIcon icon, GizmoState.Tool tool, String tip) {
        if (icons.toggleButton(id, icon, EditorStyle.iconSizeToolbar(), gizmoState.tool() == tool)) gizmoState.setTool(tool);
        tooltip(tip);
    }

    private static void tooltip(String text) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(text);
    }

    private boolean heldGesture(boolean alreadyRunning, boolean buttonHeld) {
        if (!buttonHeld) return false;
        return alreadyRunning || hovered;
    }

    private void updateCamera(float deltaSeconds) {
        camera.updateFraming(deltaSeconds);
        boolean rightHeld = heldGesture(lookGesture, ImGui.isMouseDown(ImGuiMouseButton.Right));
        lookGesture = rightHeld;
        boolean orbitHeld = heldGesture(orbitGesture, !rightHeld && ImGui.getIO().getKeyAlt() && ImGui.isMouseDown(ImGuiMouseButton.Left));
        orbitGesture = orbitHeld;
        camera.updateLook(ImGui.getMousePosX(), ImGui.getMousePosY(), rightHeld);
        camera.updateOrbit(ImGui.getMousePosX(), ImGui.getMousePosY(), orbitHeld);
        applyScrollNavigation(rightHeld);
        if (!rightHeld) return;
        ImGui.setWindowFocus();
        camera.updateMovement(keyDown(GLFW.GLFW_KEY_W), keyDown(GLFW.GLFW_KEY_S), keyDown(GLFW.GLFW_KEY_A), keyDown(GLFW.GLFW_KEY_D),
                keyDown(GLFW.GLFW_KEY_SPACE), keyDown(GLFW.GLFW_KEY_LEFT_SHIFT), keyDown(GLFW.GLFW_KEY_LEFT_CONTROL), deltaSeconds);
    }

    private static boolean keyDown(int key) {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, key);
    }

    private void applyScrollNavigation(boolean rightHeld) {
        if (!hovered) return;
        float wheel = ImGui.getIO().getMouseWheel();
        if (wheel == 0.0f) return;
        if (rightHeld) camera.applyZoom(wheel);
        else camera.applyDolly(wheel);
    }

    private void handleFrameShortcut() {
        if (!hovered || ImGui.getIO().getWantTextInput() || !ImGui.isKeyPressed(ImGuiKey.F, false)) return;
        frameSelection();
    }

    public void frameSelection() {
        List<Instance> selected = new ArrayList<>();
        for (int id : document.selection().all()) {
            Instance instance = document.find(id);
            if (instance != null && Frames.center(instance) != null) selected.add(instance);
        }
        if (selected.isEmpty()) return;
        Vector3 sum = Vector3.ZERO;
        for (Instance instance : selected) sum = sum.add(Frames.center(instance));
        Vector3 center = sum.mul(1.0 / selected.size());
        double radius = 1.0;
        for (Instance instance : selected) radius = Math.max(radius, center.distance(Frames.center(instance)) + Frames.radius(instance));
        camera.frame(new Vector3d(center.x(), center.y(), center.z()), radius);
    }

    private void handleToolHotkeys() {
        if (lookGesture || ImGui.getIO().getWantTextInput() || ImGui.getIO().getKeyCtrl() || !(hovered || ImGui.isWindowFocused())) return;
        if (ImGui.isKeyPressed(ImGuiKey.Q, false)) gizmoState.setTool(GizmoState.Tool.SELECT);
        if (ImGui.isKeyPressed(ImGuiKey.W, false)) gizmoState.setTool(GizmoState.Tool.TRANSLATE);
        if (ImGui.isKeyPressed(ImGuiKey.R, false)) gizmoState.setTool(GizmoState.Tool.ROTATE);
        if (ImGui.isKeyPressed(ImGuiKey.S, false)) gizmoState.setTool(GizmoState.Tool.SCALE);
        if (ImGui.isKeyPressed(ImGuiKey.X, false)) gizmoState.toggleSpace();
        if (ImGui.isKeyPressed(ImGuiKey.Space, false)) gizmoState.toggleAlternateTool();
    }

    private boolean renderGizmo() {
        Integer operation = gizmoState.operation().orElse(null);
        Instance leader = document.primary();
        if (operation == null || !(leader instanceof Spatial) || !document.editable(leader)) {
            dragStart.clear();
            return false;
        }
        ImGuizmo.setOrthographic(false);
        ImGuizmo.setDrawList();
        ImGuizmo.setGizmoSizeClipSpace(GIZMO_SIZE_CLIP_SPACE);
        ImGuizmo.setRect(0.0f, 0.0f, view.width(), view.height());
        Matrix4f current = Frames.matrix(leader, cameraPosition);
        current.get(model);
        if (snapActive()) {
            float step = gizmoState.snapStep();
            snap[0] = step;
            snap[1] = step;
            snap[2] = step;
            ImGuizmo.manipulate(view.view, view.projection, operation, gizmoState.mode(), model, null, snap);
        } else {
            ImGuizmo.manipulate(view.view, view.projection, operation, gizmoState.mode(), model);
        }
        boolean using = ImGuizmo.isUsing();
        if (!using) {
            dragStart.clear();
            return ImGuizmo.isOver();
        }
        if (dragStart.isEmpty()) captureDragStart(leader);
        Matrix4f moved = new Matrix4f().set(model);
        if (!moved.equals(current, 1.0e-6f)) applyDrag(leader, moved);
        return true;
    }

    private boolean snapActive() {
        return gizmoState.snapEnabled() != ImGui.getIO().getKeyCtrl();
    }

    private void captureDragStart(Instance leader) {
        dragStart.put(leader.id(), Frames.matrix(leader, cameraPosition));
        if (gizmoState.tool() == GizmoState.Tool.SCALE) return;
        List<Instance> selected = new ArrayList<>();
        for (int id : document.selection().all()) {
            Instance instance = document.find(id);
            if (instance != leader && instance instanceof Spatial && document.editable(instance)) selected.add(instance);
        }
        for (Instance instance : selected) {
            if (!hasSelectedAncestor(instance, selected, leader)) dragStart.put(instance.id(), Frames.matrix(instance, cameraPosition));
        }
    }

    private static boolean hasSelectedAncestor(Instance instance, List<Instance> selected, Instance leader) {
        for (Instance at = instance.parent(); at != null; at = at.parent()) {
            if (at == leader || selected.contains(at)) return true;
        }
        return false;
    }

    private void applyDrag(Instance leader, Matrix4f moved) {
        boolean resize = gizmoState.tool() == GizmoState.Tool.SCALE;
        Matrix4f leaderStart = dragStart.get(leader.id());
        Matrix4f delta = new Matrix4f(moved).mul(new Matrix4f(leaderStart).invert());
        List<Edit> edits = new ArrayList<>(Frames.writes(leader, moved, cameraPosition, resize));
        for (Map.Entry<Integer, Matrix4f> entry : dragStart.entrySet()) {
            if (entry.getKey() == leader.id()) continue;
            Instance follower = document.find(entry.getKey());
            if (follower == null) continue;
            edits.addAll(Frames.writes(follower, new Matrix4f(delta).mul(entry.getValue()), cameraPosition, false));
        }
        String label = switch (gizmoState.tool()) {
            case ROTATE -> "Rotate";
            case SCALE -> "Scale";
            default -> "Move";
        };
        document.history().execute(new Batch(label, edits));
    }

    private void handlePicking(boolean gizmoBusy, ImDrawList drawList) {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        if (hovered && !gizmoBusy && !lookGesture && !ImGui.getIO().getKeyAlt() && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            boxing = true;
            pressX = mouseX;
            pressY = mouseY;
        }
        if (!boxing) return;
        boolean dragged = Math.abs(mouseX - pressX) > DRAG_THRESHOLD || Math.abs(mouseY - pressY) > DRAG_THRESHOLD;
        if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            if (dragged) {
                float x0 = Math.min(pressX, mouseX);
                float y0 = Math.min(pressY, mouseY);
                float x1 = Math.max(pressX, mouseX);
                float y1 = Math.max(pressY, mouseY);
                drawList.addRectFilled(x0, y0, x1, y1, BOX_FILL);
                drawList.addRect(x0, y0, x1, y1, BOX_BORDER);
            }
            return;
        }
        boxing = false;
        if (dragged) boxSelect(Math.min(pressX, mouseX), Math.min(pressY, mouseY), Math.max(pressX, mouseX), Math.max(pressY, mouseY));
        else applyPick(pickAt(mouseX, mouseY));
    }

    private void applyPick(@Nullable Instance picked) {
        boolean additive = ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyShift();
        if (picked == null) {
            if (!additive) document.selection().clear();
            return;
        }
        if (additive) document.selection().toggle(picked.id());
        else document.selection().select(picked.id());
    }

    private void boxSelect(float x0, float y0, float x1, float y1) {
        boolean additive = ImGui.getIO().getKeyCtrl() || ImGui.getIO().getKeyShift();
        if (!additive) document.selection().clear();
        forEachEditableSpatial(instance -> {
            float[] screen = view.toScreen(Frames.center(instance));
            if (screen != null && screen[0] >= x0 && screen[0] <= x1 && screen[1] >= y0 && screen[1] <= y1) {
                document.selection().add(instance.id());
            }
        });
    }

    private @Nullable Instance pickAt(float mouseX, float mouseY) {
        Instance billboard = billboardAt(mouseX, mouseY);
        if (billboard != null) return billboard;
        Instance world = document.world();
        if (world == null) return null;
        Queries.Cast hit = Queries.raycast(world, cameraPosition, view.rayDirection(mouseX, mouseY), RAYCAST_MAX_DISTANCE, document::editable);
        return hit == null ? null : hit.part();
    }

    private @Nullable Instance billboardAt(float mouseX, float mouseY) {
        Instance[] best = {null};
        double[] closest = {BILLBOARD_CLICK_RADIUS * BILLBOARD_CLICK_RADIUS};
        forEachEditableSpatial(instance -> {
            if (instance instanceof Part) return;
            float[] screen = view.toScreen(Frames.center(instance));
            if (screen == null) return;
            double dx = screen[0] - mouseX;
            double dy = screen[1] - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance <= closest[0]) {
                closest[0] = distance;
                best[0] = instance;
            }
        });
        return best[0];
    }

    private void drawBillboards(ImDrawList drawList) {
        float half = EditorScale.of(BILLBOARD_HALF_SIZE);
        forEachEditableSpatial(instance -> {
            if (instance instanceof Part) return;
            float[] screen = view.toScreen(Frames.center(instance));
            if (screen == null) return;
            boolean selected = document.selection().isSelected(instance.id());
            drawList.addCircleFilled(screen[0], screen[1], EditorScale.of(BILLBOARD_SHADOW_RADIUS), BILLBOARD_SHADOW_COLOR);
            if (selected) drawList.addCircle(screen[0], screen[1], EditorScale.of(BILLBOARD_SHADOW_RADIUS), EditorStyle.COLOR_HIGHLIGHT, 0, OUTLINE_THICKNESS);
            drawList.addImage(icons.textureId(ClassIcons.of(instance.def())), screen[0] - half, screen[1] - half, screen[0] + half, screen[1] + half);
        });
    }

    private void drawOutlines(ImDrawList drawList) {
        if (hoveredInstance instanceof Part part && !document.selection().isSelected(part.id())) drawBox(drawList, part, HOVER_OUTLINE, 1.0f);
        for (int id : document.selection().all()) {
            if (document.find(id) instanceof Part part) drawBox(drawList, part, EditorStyle.COLOR_HIGHLIGHT, OUTLINE_THICKNESS);
        }
    }

    private void drawBox(ImDrawList drawList, Part part, int color, float thickness) {
        Vector3[] corners = Frames.corners(part);
        float[][] screen = new float[8][];
        for (int n = 0; n < 8; n++) screen[n] = view.toScreen(corners[n]);
        for (int[] edge : EDGES) {
            float[] a = screen[edge[0]];
            float[] b = screen[edge[1]];
            if (a != null && b != null) drawList.addLine(a[0], a[1], b[0], b[1], color, thickness);
        }
    }

    private void forEachEditableSpatial(java.util.function.Consumer<Instance> action) {
        Instance world = document.world();
        if (world == null) return;
        for (Instance child : world.children()) visit(child, action);
    }

    private void visit(Instance instance, java.util.function.Consumer<Instance> action) {
        if (instance instanceof Spatial && document.editable(instance)) action.accept(instance);
        for (Instance child : instance.children()) visit(child, action);
    }
}
