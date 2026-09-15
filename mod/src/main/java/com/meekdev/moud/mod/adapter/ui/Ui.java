package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.amnetic.client.surface.HudSurface;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.WorldSurface;
import com.meekdev.amnetic.client.surface.widget.Widget;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Spatial;
import com.meekdev.moud.core.interp.Motion;
import com.meekdev.moud.core.math.CFrame;
import com.meekdev.moud.core.math.Vector3;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.render.post.ScreenEffect;
import com.meekdev.moud.core.ui.BillboardGui;
import com.meekdev.moud.core.ui.GuiLayout;
import com.meekdev.moud.core.ui.GuiObject;
import com.meekdev.moud.core.ui.ScreenGui;
import com.meekdev.moud.core.ui.SurfaceGui;
import com.meekdev.moud.core.ui.TextButton;
import com.meekdev.moud.mod.adapter.render.InterfaceEffects;
import com.meekdev.moud.mod.client.ClientScene;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Vector3fc;

public final class Ui {

    private record Placed(WorldSurface surface, float width, float height, int resolution) {}

    private static final Map<Instance, Node> NODES = new HashMap<>();
    private static final Map<Instance, Placed> PLACED = new HashMap<>();

    private static HudSurface hud;
    private static InstanceTree built;
    private static long epoch = -1;
    private static boolean capturing;

    private Ui() {}

    public static void frame(float partialTick) {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) {
            clear();
            return;
        }
        if (hud == null) hud = Surfaces.hud().interactive(true);
        if (tree != built || tree.structureEpoch() != epoch) reconcile(tree);

        hud.root().children().sort((a, b) -> Integer.compare(order(a), order(b)));
        for (Widget child : hud.root().children()) {
            if (child instanceof Node node && node.source instanceof ScreenGui screen) node.visible(screen.enabled);
        }

        Motion motion = ClientScene.motion();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        for (Map.Entry<Instance, Placed> entry : new ArrayList<>(PLACED.entrySet())) {
            switch (entry.getKey()) {
                case BillboardGui billboard -> billboard(billboard, entry.getValue(), motion, camera, partialTick);
                case SurfaceGui surface -> surface(surface, entry.getValue(), motion, partialTick);
                default -> { }
            }
        }
        hover();
        buttons();
    }

    private static void buttons() {
        for (Node node : new ArrayList<>(NODES.values())) {
            if (!(node.source instanceof TextButton button) || !button.isAlive()) continue;
            boolean pressed = node.hovered && node.pressed;
            if (button.pressed != pressed) Instances.setBool(button, button.def().property("pressed"), pressed);
            if (button.hovered == node.hovered) continue;
            Instances.setBool(button, button.def().property("hovered"), node.hovered);
            if (node.hovered) button.mouseEnter.fire(button);
            else button.mouseLeave.fire(button);
        }
    }

    private static int order(Widget widget) {
        return widget instanceof Node node && node.source instanceof ScreenGui screen ? screen.displayOrder : 0;
    }

    private static void reconcile(InstanceTree tree) {
        if (tree != built) clear();
        if (hud == null) hud = Surfaces.hud().interactive(true);
        built = tree;
        epoch = tree.structureEpoch();

        NODES.keySet().removeIf(instance -> !instance.isAlive() || instance.tree() != tree);

        for (Widget child : new ArrayList<>(hud.root().children())) hud.root().removeChild(child);
        for (ScreenGui screen : tree.ofClass(Classes.SCREEN_GUI)) {
            Node node = node(screen);
            hud.root().add(node);
            attach(node);
        }

        Iterator<Map.Entry<Instance, Placed>> placed = PLACED.entrySet().iterator();
        while (placed.hasNext()) {
            Map.Entry<Instance, Placed> entry = placed.next();
            if (!entry.getKey().isAlive() || entry.getKey().tree() != tree) {
                entry.getValue().surface().remove();
                placed.remove();
            }
        }
        List<Instance> worldly = new ArrayList<>();
        worldly.addAll(tree.ofClass(Classes.BILLBOARD_GUI));
        worldly.addAll(tree.ofClass(Classes.SURFACE_GUI));
        for (Instance gui : worldly) {
            Node node = node(gui);
            attach(node);
            if (!PLACED.containsKey(gui)) PLACED.put(gui, new Placed(null, -1, -1, -1));
        }
    }

    private static Node node(Instance instance) {
        return NODES.computeIfAbsent(instance, Node::new);
    }

    private static void attach(Node parent) {
        for (Widget child : new ArrayList<>(parent.children())) parent.removeChild(child);
        for (Instance child : parent.source.children()) {
            if (!(child instanceof GuiObject)) continue;
            Node node = node(child);
            parent.add(node);
            attach(node);
        }
    }

    private static void billboard(BillboardGui gui, Placed placed, Motion motion, Camera camera,
                                  float partialTick) {
        float width = (float) Math.max(0.01, gui.size.xScale() + gui.size.xOffset() / gui.pixelsPerMetre);
        float height = (float) Math.max(0.01, gui.size.yScale() + gui.size.yOffset() / gui.pixelsPerMetre);
        WorldSurface surface = ensure(gui, placed, width, height, (int) Math.round(gui.pixelsPerMetre));
        effects(gui, surface);

        Instance target = GuiLayout.adornee(gui);
        if (!(target instanceof Spatial) || !gui.enabled) {
            surface.setVisible(false);
            return;
        }
        Vector3 at = motion.sample(target, partialTick).position().add(gui.offset);
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        surface.at(at.x(), at.y(), at.z())
                .orient(-left.x(), -left.y(), -left.z(), up.x(), up.y(), up.z())
                .alwaysOnTop(gui.alwaysOnTop)
                .maxDistance(reach(gui.maxDistance))
                .setVisible(true);
    }

    private static void surface(SurfaceGui gui, Placed placed, Motion motion, float partialTick) {
        Instance target = GuiLayout.adornee(gui);
        if (!(target instanceof Part part) || !gui.enabled) {
            if (placed.surface() != null) placed.surface().setVisible(false);
            return;
        }
        CFrame world = motion.sample(part, partialTick);
        GuiLayout.Plane plane = GuiLayout.face(world, part.size, gui.face);
        WorldSurface surface = ensure(gui, placed, (float) Math.max(0.01, plane.width()),
                (float) Math.max(0.01, plane.height()), (int) Math.round(gui.pixelsPerMetre));
        Vector3 c = plane.centre();
        Vector3 r = plane.right();
        Vector3 u = plane.up();
        effects(gui, surface);
        surface.at(c.x(), c.y(), c.z())
                .orient((float) r.x(), (float) r.y(), (float) r.z(), (float) u.x(), (float) u.y(), (float) u.z())
                .alwaysOnTop(gui.alwaysOnTop)
                .maxDistance(reach(gui.maxDistance))
                .setVisible(true);
    }

    private static void effects(Instance gui, WorldSurface surface) {
        List<ScreenEffect> found = new ArrayList<>();
        collectEffects(gui, found);
        if (found.isEmpty()) {
            surface.filter(null);
            return;
        }
        found.sort(Comparator.comparingDouble(effect -> effect.order));
        surface.filter(canvas -> {
            List<InterfaceEffects.Scoped> scoped = new ArrayList<>();
            for (ScreenEffect effect : found) {
                Node owner = effect.parent() instanceof GuiObject ? NODES.get(effect.parent()) : null;
                if (owner == null) scoped.add(new InterfaceEffects.Scoped(effect, 0, 0, canvas.width(), canvas.height()));
                else if (owner.isVisible()) scoped.add(new InterfaceEffects.Scoped(effect, owner.x, owner.y, owner.w, owner.h));
            }
            InterfaceEffects.apply(canvas, scoped);
        });
    }

    private static void collectEffects(Instance at, List<ScreenEffect> into) {
        for (Instance child : at.children()) {
            if (child instanceof ScreenEffect effect) {
                if (effect.enabled && effect.intensity > 0) into.add(effect);
            } else if (child instanceof GuiObject) {
                collectEffects(child, into);
            }
        }
    }

    private static WorldSurface ensure(Instance gui, Placed placed, float width, float height, int resolution) {
        if (placed.surface() != null && placed.width() == width && placed.height() == height
                && placed.resolution() == resolution) {
            return placed.surface();
        }
        if (placed.surface() != null) placed.surface().remove();
        WorldSurface surface = Surfaces.world(width, height).resolution(resolution).direct(true);
        surface.root().add(node(gui));
        PLACED.put(gui, new Placed(surface, width, height, resolution));
        return surface;
    }

    private static float reach(double maxDistance) {
        return maxDistance <= 0 ? Float.MAX_VALUE : (float) maxDistance;
    }

    private static void hover() {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null || client.mouseHandler.isMouseGrabbed()) {
            hud.internalInput().mouseMoved(-1, -1);
            return;
        }
        Pointer at = pointer(client);
        hud.internalInput().mouseMoved(at.x(), at.y());
    }

    public static boolean click(int button, boolean pressed) {
        Minecraft client = Minecraft.getInstance();
        if (hud == null || client.screen != null || client.getOverlay() != null) return false;
        Pointer at = pointer(client);
        if (pressed) {
            if (client.mouseHandler.isMouseGrabbed()) return false;
            capturing = hud.internalInput().mouseDown(at.x(), at.y(), button);
            return capturing;
        }
        if (!capturing) return false;
        capturing = false;
        hud.internalInput().mouseUp(at.x(), at.y(), button);
        return true;
    }

    private record Pointer(float x, float y) {}

    private static Pointer pointer(Minecraft client) {
        Window window = client.getWindow();
        double across = Math.max(1, window.getScreenWidth());
        double down = Math.max(1, window.getScreenHeight());
        return new Pointer(
                (float) (client.mouseHandler.xpos() * window.getGuiScaledWidth() / across),
                (float) (client.mouseHandler.ypos() * window.getGuiScaledHeight() / down));
    }

    private static void clear() {
        for (Placed placed : PLACED.values()) {
            if (placed.surface() != null) placed.surface().remove();
        }
        PLACED.clear();
        NODES.clear();
        if (hud != null) {
            for (Widget child : new ArrayList<>(hud.root().children())) hud.root().removeChild(child);
        }
        UiFonts.forget();
        built = null;
        epoch = -1;
        capturing = false;
    }
}
