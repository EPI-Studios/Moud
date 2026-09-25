package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.internal.InputRouter;
import com.meekdev.amnetic.client.surface.internal.UiBatcher;
import com.meekdev.amnetic.client.surface.widget.Stack;
import com.meekdev.amnetic.client.surface.widget.Widget;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.ui.GuiObject;
import com.meekdev.moud.core.ui.ScreenGui;
import com.meekdev.moud.mod.adapter.gl.RenderState;
import com.meekdev.moud.mod.adapter.gl.VertexArray;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class LoadingGui {

    private static final Stack ROOT = new Stack();
    private static final InputRouter INPUT = new InputRouter(ROOT);
    private static final Map<Instance, Node> NODES = new HashMap<>();

    private static @Nullable InstanceTree built;
    private static long epoch = -1;
    private static boolean shown;
    private static boolean capturing;

    private LoadingGui() {}

    public static void draw(InstanceTree tree, float alpha) {
        if (tree != built || tree.structureEpoch() != epoch) reconcile(tree);
        ROOT.children().sort((a, b) -> Integer.compare(order(a), order(b)));
        for (Widget child : ROOT.children()) {
            if (child instanceof Node node && node.source instanceof ScreenGui screen) node.visible(screen.enabled);
        }
        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        float width = window.getGuiScaledWidth();
        float height = window.getGuiScaledHeight();
        render(width, height, alpha);
        shown = true;
        boolean free = !client.mouseHandler.isMouseGrabbed() && client.getOverlay() == null;
        Ui.Pointer at = Ui.pointer(client);
        float x = free ? at.x() : -1;
        float y = free ? at.y() : -1;
        INPUT.mouseMoved(x, y);
        if (capturing) INPUT.mouseDragged(x, y);
        Ui.track(ROOT, ROOT, x, y);
        Ui.buttons(NODES.values());
        Ui.measured(NODES.values());
    }

    public static void hide() {
        if (!shown) return;
        shown = false;
        capturing = false;
        INPUT.mouseMoved(-1, -1);
        Ui.track(ROOT, ROOT, -1, -1);
    }

    public static void clear() {
        hide();
        INPUT.setFocus(null);
        for (Widget child : new ArrayList<>(ROOT.children())) ROOT.removeChild(child);
        NODES.clear();
        Ui.forgetHover(ROOT);
        built = null;
        epoch = -1;
    }

    public static boolean click(int button, boolean pressed) {
        Minecraft client = Minecraft.getInstance();
        if (!shown || client.mouseHandler.isMouseGrabbed() || client.getOverlay() != null) return false;
        Ui.Pointer at = Ui.pointer(client);
        List<Node> chain = Ui.chain(ROOT, at.x(), at.y());
        boolean sunk = false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            sunk = Ui.fire(chain, object -> pressed ? object.mouseButton1Down : object.mouseButton1Up, at.x(), at.y());
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !pressed) {
            sunk = Ui.fire(chain, object -> object.mouseButton2Click, at.x(), at.y());
        }
        if (pressed) {
            capturing = INPUT.mouseDown(at.x(), at.y(), button);
            return capturing || sunk;
        }
        if (!capturing) return sunk;
        capturing = false;
        INPUT.mouseUp(at.x(), at.y(), button);
        return true;
    }

    private static void reconcile(InstanceTree tree) {
        if (tree != built) clear();
        built = tree;
        epoch = tree.structureEpoch();
        NODES.keySet().removeIf(instance -> !instance.isAlive() || instance.tree() != tree);
        for (Widget child : new ArrayList<>(ROOT.children())) ROOT.removeChild(child);
        for (ScreenGui screen : tree.ofClass(Classes.SCREEN_GUI)) {
            if (Instance.outOfWorld(screen)) continue;
            Node node = node(screen);
            attach(node);
            ROOT.add(node);
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

    private static int order(Widget widget) {
        return widget instanceof Node node && node.source instanceof ScreenGui screen ? screen.displayOrder : 0;
    }

    private static void render(float width, float height, float alpha) {
        int previous = MainTargetFramebuffer.bind();
        if (previous < 0) return;
        UiBatcher batcher = UiBatcher.INSTANCE;
        try {
            RenderState.noScissor();
            RenderState.blend(true);
            VertexArray.unbindBuffer();
            RenderState.alphaBlend();
            RenderState.depthTest(false);
            RenderState.depthMask(false);
            RenderState.cull(false);
            batcher.begin(width, height);
            UiDraw draw = new UiDraw(batcher, width, height);
            ROOT.layout(0, 0, width, height);
            ROOT.draw(draw, alpha);
            batcher.flush();
        } finally {
            MainTargetFramebuffer.restore(previous);
            GlState.endFullscreen();
        }
    }
}
