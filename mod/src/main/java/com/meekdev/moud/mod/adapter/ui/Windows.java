package com.meekdev.moud.mod.adapter.ui;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.internal.InputRouter;
import com.meekdev.amnetic.client.surface.internal.UiBatcher;
import com.meekdev.amnetic.client.surface.widget.Stack;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.clazz.PropertyDef;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.instance.Transforms;
import com.meekdev.moud.core.part.Part;
import com.meekdev.moud.core.render.Camera;
import com.meekdev.moud.core.ui.AppWindow;
import com.meekdev.moud.core.ui.GuiObject;
import com.meekdev.moud.core.ui.ScreenGui;
import com.meekdev.moud.core.ui.ViewportFrame;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.gl.RenderState;
import com.meekdev.moud.mod.adapter.gl.VertexArray;
import com.meekdev.moud.mod.adapter.gl.WindowContext;
import com.meekdev.moud.mod.adapter.render.Viewports;
import com.meekdev.moud.mod.client.ClientScene;
import com.meekdev.moud.mod.place.Game;
import com.meekdev.moud.mod.place.Output;
import com.mojang.blaze3d.opengl.GlTexture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;

public final class Windows {

    private static final long SECOND_CLOSE_MILLIS = 10_000;

    private static final class Open {
        final AppWindow source;
        final long handle;
        final WindowContext context;
        final Stack root = new Stack();
        final InputRouter input = new InputRouter(root);
        @Nullable Framebuffer canvas;
        @Nullable Node gui;
        double x;
        double y;
        double width;
        double height;
        String title = "";
        boolean visible;
        boolean decorated;
        boolean alwaysOnTop;
        boolean passthrough;
        boolean resizable;
        double opacity = -1;
        float mouseX;
        float mouseY;
        boolean dragging;
        long keptAt;
        boolean closeAsked;

        Open(AppWindow source, long handle, WindowContext context) {
            this.source = source;
            this.handle = handle;
            this.context = context;
        }
    }

    private static final Map<AppWindow, Open> OPEN = new IdentityHashMap<>();
    private static final Map<Long, Open> BY_HANDLE = new HashMap<>();
    private static boolean refusedOnce;

    private Windows() {}

    public static boolean allowed() {
        Minecraft client = Minecraft.getInstance();
        return Game.standalone() || client.hasSingleplayerServer();
    }

    static boolean claims(ScreenGui screen) {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return false;
        for (AppWindow window : tree.ofClass(Classes.WINDOW)) {
            if (window.isAlive() && window.gui == screen) return true;
        }
        return false;
    }

    static void layoutGuis() {
        for (Open open : OPEN.values()) {
            if (open.gui != null && open.source.gui instanceof ScreenGui screen) open.gui.visible(screen.enabled);
        }
    }

    public static void frame() {
        InstanceTree tree = ClientScene.tree();
        List<AppWindow> wanted = tree == null ? List.of() : List.copyOf(tree.ofClass(Classes.WINDOW));
        for (AppWindow window : new ArrayList<>(OPEN.keySet())) {
            if (!window.isAlive() || !wanted.contains(window)) close(OPEN.get(window));
        }
        if (escapeHatch()) {
            for (AppWindow window : wanted) if (window.isAlive()) Instances.destroy(window);
            GLFW.glfwShowWindow(mainHandle());
            return;
        }
        for (AppWindow window : wanted) {
            if (!window.isAlive() || OPEN.containsKey(window)) continue;
            if (!allowed()) {
                if (!refusedOnce) Output.add(Output.Level.WARN, "client", "Extra windows and overlays only open in single player and exported games");
                refusedOnce = true;
                Instances.destroy(window);
                continue;
            }
            open(window);
        }
        if (OPEN.isEmpty()) {
            if (GLFW.glfwGetWindowAttrib(mainHandle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE) GLFW.glfwShowWindow(mainHandle());
            return;
        }
        for (Open open : new ArrayList<>(OPEN.values())) {
            try {
                sync(open);
                if (OPEN.containsKey(open.source)) draw(open);
            } catch (RuntimeException e) {
                MoudMod.LOG.error("window {} failed and was closed", open.source.title, e);
                Instances.destroy(open.source);
                close(open);
            }
        }
    }

    private static long mainHandle() {
        return Minecraft.getInstance().getWindow().handle();
    }

    private static boolean escapeHatch() {
        List<Long> handles = new ArrayList<>(BY_HANDLE.keySet());
        handles.add(mainHandle());
        for (long handle : handles) {
            boolean control = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            boolean shift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (control && shift && GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) return !OPEN.isEmpty() || GLFW.glfwGetWindowAttrib(mainHandle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE;
        }
        return false;
    }

    private static void open(AppWindow source) {
        long main = mainHandle();
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, GLFW.glfwGetWindowAttrib(main, GLFW.GLFW_CONTEXT_VERSION_MAJOR));
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, GLFW.glfwGetWindowAttrib(main, GLFW.GLFW_CONTEXT_VERSION_MINOR));
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_TRANSPARENT_FRAMEBUFFER, source.transparent ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, source.decorated ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FLOATING, source.alwaysOnTop ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, source.resizable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_MOUSE_PASSTHROUGH, source.clickThrough ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long handle = GLFW.glfwCreateWindow(size(source.width), size(source.height), source.title.isEmpty() ? "Window" : source.title, 0, main);
        GLFW.glfwDefaultWindowHints();
        if (handle == 0) {
            Output.add(Output.Level.WARN, "client", "The system refused to open a window");
            Instances.destroy(source);
            return;
        }
        Open open = new Open(source, handle, WindowContext.create(handle));
        open.x = Double.NaN;
        OPEN.put(source, open);
        BY_HANDLE.put(handle, open);
        callbacks(open);
    }

    private static void callbacks(Open open) {
        GLFW.glfwSetCursorPosCallback(open.handle, (handle, x, y) -> {
            float scale = scale();
            open.mouseX = (float) (x / scale);
            open.mouseY = (float) (y / scale);
            open.input.mouseMoved(open.mouseX, open.mouseY);
            if (open.dragging) open.input.mouseDragged(open.mouseX, open.mouseY);
        });
        GLFW.glfwSetMouseButtonCallback(open.handle, (handle, button, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                open.dragging = open.input.mouseDown(open.mouseX, open.mouseY, button);
            } else if (action == GLFW.GLFW_RELEASE) {
                open.dragging = false;
                open.input.mouseUp(open.mouseX, open.mouseY, button);
            }
        });
        GLFW.glfwSetScrollCallback(open.handle, (handle, dx, dy) -> open.input.scroll(open.mouseX, open.mouseY, (float) dy));
        GLFW.glfwSetKeyCallback(open.handle, (handle, key, scancode, action, mods) -> {
            if (action != GLFW.GLFW_RELEASE) open.input.keyPressed(key, mods);
        });
        GLFW.glfwSetCharCallback(open.handle, (handle, codepoint) -> open.input.charTyped(codepoint));
        GLFW.glfwSetWindowCloseCallback(open.handle, handle -> open.closeAsked = true);
    }

    private static void sync(Open open) {
        AppWindow w = open.source;
        long handle = open.handle;
        if (open.closeAsked || GLFW.glfwWindowShouldClose(handle)) {
            open.closeAsked = false;
            GLFW.glfwSetWindowShouldClose(handle, false);
            long now = System.currentTimeMillis();
            w.takeKeptOpen();
            w.closing.fire(w);
            if (!w.takeKeptOpen() || now - open.keptAt < SECOND_CLOSE_MILLIS) {
                Instances.destroy(w);
                close(open);
                return;
            }
            open.keptAt = now;
        }
        int[] ax = new int[1];
        int[] ay = new int[1];
        GLFW.glfwGetWindowPos(handle, ax, ay);
        if (Double.isNaN(open.x) || w.x != open.x || w.y != open.y) {
            GLFW.glfwSetWindowPos(handle, (int) Math.round(w.x), (int) Math.round(w.y));
            open.x = w.x;
            open.y = w.y;
        } else if (ax[0] != (int) Math.round(open.x) || ay[0] != (int) Math.round(open.y)) {
            open.x = ax[0];
            open.y = ay[0];
            set(w, "x", ax[0]);
            set(w, "y", ay[0]);
            w.moved.fire(w);
        }
        int[] aw = new int[1];
        int[] ah = new int[1];
        GLFW.glfwGetWindowSize(handle, aw, ah);
        if (w.width != open.width || w.height != open.height) {
            GLFW.glfwSetWindowSize(handle, size(w.width), size(w.height));
            open.width = w.width;
            open.height = w.height;
        } else if (aw[0] > 0 && ah[0] > 0 && (aw[0] != size(open.width) || ah[0] != size(open.height))) {
            open.width = aw[0];
            open.height = ah[0];
            set(w, "width", aw[0]);
            set(w, "height", ah[0]);
            w.resized.fire(w);
        }
        if (!w.title.equals(open.title)) {
            GLFW.glfwSetWindowTitle(handle, w.title.isEmpty() ? "Window" : w.title);
            open.title = w.title;
        }
        attribute(handle, GLFW.GLFW_DECORATED, w.decorated, open.decorated, value -> open.decorated = value);
        attribute(handle, GLFW.GLFW_FLOATING, w.alwaysOnTop, open.alwaysOnTop, value -> open.alwaysOnTop = value);
        attribute(handle, GLFW.GLFW_RESIZABLE, w.resizable, open.resizable, value -> open.resizable = value);
        boolean passthrough = w.clickThrough && !overClickable(open);
        attribute(handle, GLFW.GLFW_MOUSE_PASSTHROUGH, passthrough, open.passthrough, value -> open.passthrough = value);
        if (w.opacity != open.opacity) {
            GLFW.glfwSetWindowOpacity(handle, (float) w.opacity);
            open.opacity = w.opacity;
        }
        if (w.visible != open.visible) {
            if (w.visible) GLFW.glfwShowWindow(handle);
            else GLFW.glfwHideWindow(handle);
            open.visible = w.visible;
        }
        if (w.takeFocusWanted()) GLFW.glfwFocusWindow(handle);
        boolean focused = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        if (focused != w.focused) Instances.setBool(w, property(w, "focused"), focused);
        claimGui(open);
    }

    private interface Setter {
        void set(boolean value);
    }

    private static void attribute(long handle, int attribute, boolean wanted, boolean applied, Setter remember) {
        if (wanted == applied) return;
        GLFW.glfwSetWindowAttrib(handle, attribute, wanted ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        remember.set(wanted);
    }

    private static boolean overClickable(Open open) {
        if (!(open.source.clickable instanceof GuiObject target) || open.gui == null) return false;
        Node node = Ui.node(target);
        double[] cx = new double[1];
        double[] cy = new double[1];
        GLFW.glfwGetCursorPos(open.handle, cx, cy);
        float scale = scale();
        float mx = (float) (cx[0] / scale);
        float my = (float) (cy[0] / scale);
        return node.isVisible() && mx >= node.x && my >= node.y && mx < node.x + node.w && my < node.y + node.h;
    }

    private static void claimGui(Open open) {
        Node wanted = open.source.gui instanceof ScreenGui screen && screen.isAlive() ? Ui.node(screen) : null;
        if (wanted == open.gui) return;
        if (open.gui != null) {
            open.root.removeChild(open.gui);
            Ui.invalidate();
        }
        open.gui = wanted;
        if (wanted != null) {
            Ui.release(wanted);
            Ui.attach(wanted);
            open.root.add(wanted);
        }
    }

    private static void draw(Open open) {
        int[] fw = new int[1];
        int[] fh = new int[1];
        GLFW.glfwGetFramebufferSize(open.handle, fw, fh);
        if (fw[0] < 1 || fh[0] < 1 || !open.visible) return;
        int texture = content(open, fw[0], fh[0]);
        open.context.present(texture, fw[0], fh[0], open.source.transparent);
    }

    private static int content(Open open, int width, int height) {
        AppWindow w = open.source;
        if (w.mirror) {
            return Minecraft.getInstance().getMainRenderTarget().getColorTexture() instanceof GlTexture gl ? gl.glId() : 0;
        }
        Framebuffer canvas = open.canvas;
        if (canvas == null || canvas.width() != width || canvas.height() != height) {
            if (canvas != null) canvas.dispose();
            canvas = Framebuffers.fixed(width, height, FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build());
            open.canvas = canvas;
        }
        if (w.camera instanceof Camera camera && camera.isAlive()) {
            Viewports.view(canvas, Transforms.world(camera), camera.fov > 0 ? camera.fov : 70, worldParts());
            if (open.gui != null) gui(open, canvas, false);
            return canvas.colorTextureGlId(0);
        }
        if (open.gui != null) {
            gui(open, canvas, true);
            return canvas.colorTextureGlId(0);
        }
        canvas.begin();
        canvas.clear(0, 0, 0, 0);
        canvas.end();
        return canvas.colorTextureGlId(0);
    }

    private static void gui(Open open, Framebuffer canvas, boolean clear) {
        float scale = scale();
        float width = canvas.width() / scale;
        float height = canvas.height() / scale;
        UiBatcher batcher = UiBatcher.INSTANCE;
        canvas.begin();
        try {
            if (clear) canvas.clear(0, 0, 0, 0);
            RenderState.blend(true);
            VertexArray.unbindBuffer();
            RenderState.alphaBlend();
            RenderState.depthTest(false);
            RenderState.cull(false);
            batcher.begin(width, height);
            UiDraw draw = new UiDraw(batcher, width, height);
            open.root.layout(0, 0, width, height);
            open.root.draw(draw, 1f);
            batcher.flush();
        } finally {
            canvas.end();
            GlState.endFullscreen();
        }
    }

    private static List<Part> worldParts() {
        InstanceTree tree = ClientScene.tree();
        if (tree == null) return List.of();
        List<Part> parts = new ArrayList<>();
        for (Part part : tree.ofClass(Classes.PART)) {
            if (!ViewportFrame.inside(part)) parts.add(part);
        }
        return parts;
    }

    private static float scale() {
        return (float) Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }

    private static void close(@Nullable Open open) {
        if (open == null) return;
        OPEN.remove(open.source);
        BY_HANDLE.remove(open.handle);
        if (open.gui != null) {
            open.root.removeChild(open.gui);
            Ui.invalidate();
        }
        if (open.canvas != null) open.canvas.dispose();
        open.context.close();
        Callbacks.glfwFreeCallbacks(open.handle);
        GLFW.glfwDestroyWindow(open.handle);
    }

    private static PropertyDef property(Instance instance, String name) {
        return instance.def().property(name);
    }

    private static void set(Instance instance, String name, double value) {
        Instances.setNum(instance, property(instance, name), value);
    }

    private static int size(double value) {
        return Math.max(1, (int) Math.round(value));
    }

    public static void closeAll() {
        for (Open open : new ArrayList<>(OPEN.values())) close(open);
    }
}
