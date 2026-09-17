package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.amnetic.client.surface.HudSurface;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.WorldSurface;
import com.meekdev.amnetic.client.surface.internal.InputRouter;
import com.meekdev.amnetic.client.surface.widget.Widget;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.event.Signal;
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
import com.meekdev.moud.core.ui.ImageButton;
import com.meekdev.moud.core.ui.ScreenGui;
import com.meekdev.moud.core.ui.ScrollingFrame;
import com.meekdev.moud.core.ui.SurfaceGui;
import com.meekdev.moud.core.ui.TextBox;
import com.meekdev.moud.core.ui.TextButton;
import com.meekdev.moud.core.ui.TextLabel;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.adapter.render.InterfaceEffects;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.client.EditMode;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class Ui {

    private record Placed(WorldSurface surface, float width, float height, int resolution) {}

    private record Pointer(float x, float y) {}

    private static final class Hover {
        Set<Node> inside = Set.of();
        float x = -1;
        float y = -1;
    }

    private static final Map<Instance, Node> NODES = new HashMap<>();
    private static final Map<Instance, Placed> PLACED = new HashMap<>();
    private static final Map<Instance, Boolean> PREVIEWED = new HashMap<>();
    private static final Map<Object, Hover> HOVERS = new IdentityHashMap<>();

    private static HudSurface hud;
    private static InstanceTree built;
    private static long epoch = -1;
    private static boolean capturing;
    private static boolean leftWasDown;
    private static boolean rightWasDown;

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
        boolean editing = EditMode.editing();
        if (!editing) PREVIEWED.clear();
        for (Widget child : hud.root().children()) {
            if (child instanceof Node node && node.source instanceof ScreenGui screen) {
                Boolean previewed = editing ? PREVIEWED.get(screen) : null;
                node.visible(previewed != null ? previewed : screen.enabled);
            }
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
        measured();
        focusRequests();
        pointers();
        Windows.layoutGuis();
    }

    private static void buttons() {
        for (Node node : new ArrayList<>(NODES.values())) {
            if (!(node.source instanceof TextButton || node.source instanceof ImageButton) || !node.source.isAlive()) continue;
            Instance button = node.source;
            Instances.setBool(button, button.def().property("pressed"), node.hovered && node.pressed);
            Instances.setBool(button, button.def().property("hovered"), node.hovered);
        }
    }

    private static void measured() {
        for (Node node : NODES.values()) {
            if (!(node.source instanceof GuiObject object) || !object.isAlive()) continue;
            Vector3 position = new Vector3(node.scale * node.x + node.offsetX, node.scale * node.y + node.offsetY, 0);
            Vector3 size = new Vector3(node.scale * node.w, node.scale * node.h, 0);
            write(object, "absolutePosition", position);
            write(object, "absoluteSize", size);
            if (object instanceof TextLabel) write(object, "textBounds", new Vector3(node.boundsW, node.boundsH, 0));
            if (object instanceof ScrollingFrame && node.canvas != null) {
                write(object, "absoluteCanvasSize", new Vector3(node.canvas.w(), node.canvas.h(), 0));
                write(object, "absoluteWindowSize", new Vector3(node.w, node.h, 0));
            }
        }
    }

    private static void write(Instance instance, String name, Vector3 value) {
        Instances.setObj(instance, instance.def().property(name), value);
    }

    private static void focusRequests() {
        for (Node node : new ArrayList<>(NODES.values())) {
            if (!(node.source instanceof TextBox box)) continue;
            InputRouter router = routerOf(node);
            if (!box.isAlive()) {
                if (router != null && router.focused() == node) router.setFocus(null);
                continue;
            }
            switch (box.takeRequest()) {
                case CAPTURE -> {
                    if (router != null && router.focused() != node && shown(node)) router.setFocus(node);
                }
                case RELEASE -> blur(node);
                case NONE -> { }
            }
        }
    }

    private static boolean shown(Widget widget) {
        for (Widget at = widget; at != null; at = at.parentWidget()) {
            if (!at.isVisible()) return false;
        }
        return true;
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
            attach(node);
            if (!Windows.claims(screen)) hud.root().add(node);
        }

        Iterator<Map.Entry<Instance, Placed>> placed = PLACED.entrySet().iterator();
        while (placed.hasNext()) {
            Map.Entry<Instance, Placed> entry = placed.next();
            if (!entry.getKey().isAlive() || entry.getKey().tree() != tree) {
                if (entry.getValue().surface() != null) {
                    HOVERS.remove(entry.getValue().surface());
                    entry.getValue().surface().remove();
                }
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

    public static @Nullable Boolean previewed(ScreenGui screen) {
        return PREVIEWED.get(screen);
    }

    public static void preview(ScreenGui screen, @Nullable Boolean shown) {
        if (shown == null) PREVIEWED.remove(screen);
        else PREVIEWED.put(screen, shown);
    }

    public static void clearPreviews() {
        PREVIEWED.clear();
    }

    static void invalidate() {
        epoch = -1;
    }

    static void release(Node node) {
        if (hud != null) hud.root().removeChild(node);
    }

    static Node node(Instance instance) {
        return NODES.computeIfAbsent(instance, Node::new);
    }

    static void attach(Node parent) {
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
                if (effect.enabled && effect.intensity > 0 && !(at instanceof ViewportFrame)) into.add(effect);
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
        if (placed.surface() != null) {
            HOVERS.remove(placed.surface());
            placed.surface().remove();
        }
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
        if (capturing) hud.internalInput().mouseDragged(at.x(), at.y());
    }

    private static boolean pointerFree(Minecraft client) {
        return client.screen == null && client.getOverlay() == null && !client.mouseHandler.isMouseGrabbed();
    }

    private static void pointers() {
        Minecraft client = Minecraft.getInstance();
        Pointer at = pointer(client);
        boolean free = pointerFree(client);
        track(hud, hud.internalTree(), free ? at.x() : -1, free ? at.y() : -1);

        Window window = client.getWindow();
        boolean left = client.screen == null && GLFW.glfwGetMouseButton(window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = client.screen == null && GLFW.glfwGetMouseButton(window.handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        WorldSurface aimed = null;
        for (Placed placed : PLACED.values()) {
            WorldSurface surface = placed.surface();
            if (surface == null) continue;
            InputRouter router = surface.internalInput();
            boolean on = surface.isVisible() && router.mouseX() >= 0 && router.mouseY() >= 0;
            float x = on ? router.mouseX() : -1;
            float y = on ? router.mouseY() : -1;
            track(surface, surface.internalTree(), x, y);
            if (!on) continue;
            aimed = surface;
            List<Node> chain = chain(surface.internalTree(), x, y);
            if (left && !leftWasDown) fire(chain, object -> object.mouseButton1Down, x, y);
            if (!left && leftWasDown) fire(chain, object -> object.mouseButton1Up, x, y);
            if (!right && rightWasDown) fire(chain, object -> object.mouseButton2Click, x, y);
        }
        if (left && !leftWasDown) {
            for (Placed placed : PLACED.values()) {
                if (placed.surface() != null && placed.surface() != aimed && placed.surface().internalInput().focused() != null) {
                    placed.surface().internalInput().setFocus(null);
                }
            }
        }
        leftWasDown = left;
        rightWasDown = right;
    }

    private static void track(Object key, Widget root, float x, float y) {
        Hover hover = HOVERS.computeIfAbsent(key, k -> new Hover());
        List<Node> under = new ArrayList<>();
        if (x >= 0 && y >= 0) collect(root, x, y, under);
        Set<Node> inside = new LinkedHashSet<>(under);
        boolean moved = x != hover.x || y != hover.y;
        for (Node node : hover.inside) {
            if (!inside.contains(node) && node.source instanceof GuiObject object && object.isAlive()) object.mouseLeave.fire(object);
        }
        for (Node node : inside) {
            if (!(node.source instanceof GuiObject object) || !object.isAlive()) continue;
            if (!hover.inside.contains(node)) object.mouseEnter.fire(object);
            else if (moved) object.mouseMoved.fire(new Object[] {(double) x, (double) y});
        }
        hover.inside = inside;
        hover.x = x;
        hover.y = y;
    }

    private static void collect(Widget at, float x, float y, List<Node> into) {
        for (Widget child : at.children()) {
            if (!(child instanceof Node node)) {
                if (child.isVisible()) collect(child, x, y, into);
                continue;
            }
            if (!node.isVisible()) continue;
            boolean inside = node.contains(x, y);
            if (node.source instanceof GuiObject && inside) into.add(node);
            if (inside || !node.clips()) collect(node, x, y, into);
        }
    }

    private static List<Node> chain(Widget root, float x, float y) {
        List<Node> under = new ArrayList<>();
        collect(root, x, y, under);
        return under.reversed();
    }

    private static boolean fire(List<Node> chain, Function<GuiObject, Signal<Object[]>> signal, float x, float y) {
        for (Node node : chain) {
            if (!(node.source instanceof GuiObject object) || !object.isAlive()) continue;
            signal.apply(object).fire(new Object[] {(double) x, (double) y});
            if (object.sinksInput()) return true;
        }
        return false;
    }

    public static boolean click(int button, boolean pressed) {
        Minecraft client = Minecraft.getInstance();
        if (hud == null || client.screen != null || client.getOverlay() != null) return false;
        Pointer at = pointer(client);
        if (pressed && client.mouseHandler.isMouseGrabbed()) {
            if (hud.internalInput().focused() != null) hud.internalInput().setFocus(null);
            return false;
        }
        if (!client.mouseHandler.isMouseGrabbed()) {
            List<Node> chain = chain(hud.internalTree(), at.x(), at.y());
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                fire(chain, object -> pressed ? object.mouseButton1Down : object.mouseButton1Up, at.x(), at.y());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !pressed) {
                fire(chain, object -> object.mouseButton2Click, at.x(), at.y());
            }
        }
        if (pressed) {
            capturing = hud.internalInput().mouseDown(at.x(), at.y(), button);
            return capturing;
        }
        if (!capturing) return false;
        capturing = false;
        hud.internalInput().mouseUp(at.x(), at.y(), button);
        return true;
    }

    public static boolean scroll(double amount) {
        Minecraft client = Minecraft.getInstance();
        if (hud == null || client.screen != null || client.getOverlay() != null || amount == 0) return false;
        if (pointerFree(client)) {
            Pointer at = pointer(client);
            List<Node> chain = chain(hud.internalTree(), at.x(), at.y());
            if (!chain.isEmpty()) {
                boolean sunk = fire(chain, object -> amount > 0 ? object.mouseWheelForward : object.mouseWheelBackward, at.x(), at.y());
                return hud.internalInput().scroll(at.x(), at.y(), (float) amount) || sunk;
            }
        }
        for (Placed placed : PLACED.values()) {
            WorldSurface surface = placed.surface();
            if (surface == null || !surface.isVisible()) continue;
            InputRouter router = surface.internalInput();
            if (router.mouseX() < 0 || router.mouseY() < 0) continue;
            List<Node> chain = chain(surface.internalTree(), router.mouseX(), router.mouseY());
            if (chain.isEmpty()) continue;
            boolean sunk = fire(chain, object -> amount > 0 ? object.mouseWheelForward : object.mouseWheelBackward,
                    router.mouseX(), router.mouseY());
            return router.scroll((float) amount) || sunk;
        }
        return false;
    }

    public static boolean typing(int action, int key, int scancode, int modifiers) {
        InputRouter router = focusedRouter();
        if (router == null) return false;
        if (action != GLFW.GLFW_RELEASE) router.keyPressed(layoutKey(key, scancode), modifiers);
        return true;
    }

    public static boolean typed(int codepoint) {
        InputRouter router = focusedRouter();
        if (router == null) return false;
        router.charTyped(codepoint);
        return true;
    }

    private static int layoutKey(int key, int scancode) {
        if (key < GLFW.GLFW_KEY_A || key > GLFW.GLFW_KEY_Z) return key;
        String name = GLFW.glfwGetKeyName(key, scancode);
        if (name == null || name.length() != 1) return key;
        char letter = Character.toUpperCase(name.charAt(0));
        return letter >= 'A' && letter <= 'Z' ? GLFW.GLFW_KEY_A + (letter - 'A') : key;
    }

    private static @Nullable InputRouter focusedRouter() {
        Minecraft client = Minecraft.getInstance();
        if (hud == null || client.screen != null || client.getOverlay() != null) return null;
        if (hud.internalInput().focused() instanceof Node) return hud.internalInput();
        for (Placed placed : PLACED.values()) {
            if (placed.surface() != null && placed.surface().internalInput().focused() instanceof Node) {
                return placed.surface().internalInput();
            }
        }
        return null;
    }

    private static @Nullable InputRouter routerOf(Node node) {
        Widget top = node;
        while (top.parentWidget() != null) top = top.parentWidget();
        if (hud != null && top == hud.internalTree()) return hud.internalInput();
        for (Placed placed : PLACED.values()) {
            if (placed.surface() != null && top == placed.surface().internalTree()) return placed.surface().internalInput();
        }
        return Windows.input(top);
    }

    static void focusMoved(Node node) {
        InputRouter own = routerOf(node);
        List<InputRouter> others = new ArrayList<>();
        if (hud != null) others.add(hud.internalInput());
        for (Placed placed : PLACED.values()) {
            if (placed.surface() != null) others.add(placed.surface().internalInput());
        }
        for (InputRouter router : others) {
            if (router != own && router.focused() != null) router.setFocus(null);
        }
    }

    static void blur(Node node) {
        InputRouter router = routerOf(node);
        if (router != null && router.focused() == node) router.setFocus(null);
    }

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
        HOVERS.clear();
        if (hud != null) {
            hud.internalInput().setFocus(null);
            for (Widget child : new ArrayList<>(hud.root().children())) hud.root().removeChild(child);
        }
        UiFonts.forget();
        built = null;
        epoch = -1;
        capturing = false;
    }
}
