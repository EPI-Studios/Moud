package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.ui.Windows;
import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.script.api.WindowRef;
import com.mojang.blaze3d.platform.Window;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

public final class WindowApi implements WindowRef {

    public static final WindowApi INSTANCE = new WindowApi();

    private static final long READ_BACK_MILLIS = 250;
    private static final long SECOND_CLOSE_MILLIS = 10_000;
    private static final Map<String, Integer> CURSORS = Map.of(
            "arrow", GLFW.GLFW_ARROW_CURSOR,
            "hand", GLFW.GLFW_POINTING_HAND_CURSOR,
            "text", GLFW.GLFW_IBEAM_CURSOR,
            "crosshair", GLFW.GLFW_CROSSHAIR_CURSOR,
            "resizeEW", GLFW.GLFW_RESIZE_EW_CURSOR,
            "resizeNS", GLFW.GLFW_RESIZE_NS_CURSOR,
            "resizeAll", GLFW.GLFW_RESIZE_ALL_CURSOR,
            "notAllowed", GLFW.GLFW_NOT_ALLOWED_CURSOR);

    private @Nullable String title;
    private String icon = "";
    private String cursor = "arrow";
    private long cursorHandle;
    private @Nullable String hovering;
    private long hoverHandle;
    private boolean cursorVisible = true;
    private double opacity = 1;
    private boolean resizable = true;
    private int minWidth;
    private int minHeight;
    private int setX;
    private int setY;
    private long setAt;
    private boolean prevented;
    private long preventedAt;

    private WindowApi() {}

    private static long handle() {
        return Minecraft.getInstance().getWindow().handle();
    }

    private static void render(Runnable action) {
        Minecraft client = Minecraft.getInstance();
        if (client.isSameThread()) action.run();
        else client.execute(action);
    }

    public void reset() {
        render(() -> {
            title = null;
            Minecraft.getInstance().updateTitle();
            if (!cursor.equals("arrow")) cursor("arrow");
            if (!cursorVisible) cursorVisible(true);
            if (opacity != 1) opacity(1);
            if (!resizable) resizable(true);
            if (minWidth != 0 || minHeight != 0) minSize(0, 0);
        });
    }

    public @Nullable String titleOverride() {
        return title;
    }

    public boolean holdClose() {
        long now = System.currentTimeMillis();
        if (now - preventedAt < SECOND_CLOSE_MILLIS) return false;
        prevented = false;
        if (!ClientPlace.windowClosing() || !prevented) return false;
        preventedAt = now;
        GLFW.glfwSetWindowShouldClose(handle(), false);
        return true;
    }

    @Override
    public String title() {
        if (title != null) return title;
        return PlaceToml.opened() ? PlaceToml.config().name() : "Minecraft";
    }

    @Override
    public void title(String value) {
        render(() -> {
            title = value;
            Minecraft.getInstance().updateTitle();
        });
    }

    @Override
    public String icon() {
        return icon;
    }

    @Override
    public void icon(String res) {
        icon = res;
        render(() -> applyIcon(res));
    }

    private static void applyIcon(String res) {
        Path root = PlaceToml.root();
        try {
            byte[] bytes = Files.readAllBytes(root.resolve(Res.parse(res)));
            ByteBuffer encoded = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, w, h, channels, 4);
                if (pixels == null) throw new IOException(STBImage.stbi_failure_reason());
                try {
                    GLFWImage.Buffer images = GLFWImage.malloc(1, stack);
                    images.position(0).width(w.get(0)).height(h.get(0)).pixels(pixels);
                    GLFW.glfwSetWindowIcon(handle(), images);
                } finally {
                    STBImage.stbi_image_free(pixels);
                }
            }
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.warn("could not use {} as the window icon: {}", res, e.getMessage());
        }
    }

    @Override
    public boolean fullscreen() {
        return Minecraft.getInstance().getWindow().isFullscreen();
    }

    @Override
    public void fullscreen(boolean on) {
        render(() -> {
            Window window = Minecraft.getInstance().getWindow();
            if (window.isFullscreen() != on) window.toggleFullScreen();
        });
    }

    @Override
    public int width() {
        return Minecraft.getInstance().getWindow().getScreenWidth();
    }

    @Override
    public int height() {
        return Minecraft.getInstance().getWindow().getScreenHeight();
    }

    @Override
    public void resize(int width, int height) {
        render(() -> GLFW.glfwSetWindowSize(handle(), width, height));
    }

    @Override
    public int x() {
        if (System.currentTimeMillis() - setAt < READ_BACK_MILLIS) return setX;
        int[] x = new int[1];
        int[] y = new int[1];
        GLFW.glfwGetWindowPos(handle(), x, y);
        return x[0];
    }

    @Override
    public int y() {
        if (System.currentTimeMillis() - setAt < READ_BACK_MILLIS) return setY;
        int[] x = new int[1];
        int[] y = new int[1];
        GLFW.glfwGetWindowPos(handle(), x, y);
        return y[0];
    }

    @Override
    public void moveTo(int x, int y) {
        setX = x;
        setY = y;
        setAt = System.currentTimeMillis();
        render(() -> GLFW.glfwSetWindowPos(handle(), x, y));
    }

    @Override
    public void center() {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode mode = monitor == 0 ? null : GLFW.glfwGetVideoMode(monitor);
        if (mode == null) return;
        int[] mx = new int[1];
        int[] my = new int[1];
        GLFW.glfwGetMonitorPos(monitor, mx, my);
        moveTo(mx[0] + (mode.width() - width()) / 2, my[0] + (mode.height() - height()) / 2);
    }

    @Override
    public boolean canMove() {
        return GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND;
    }

    @Override
    public int minWidth() {
        return minWidth;
    }

    @Override
    public int minHeight() {
        return minHeight;
    }

    @Override
    public void minSize(int width, int height) {
        minWidth = width;
        minHeight = height;
        render(() -> GLFW.glfwSetWindowSizeLimits(handle(), limit(width), limit(height), GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE));
    }

    private static int limit(int size) {
        return size <= 0 ? GLFW.GLFW_DONT_CARE : size;
    }

    @Override
    public boolean resizable() {
        return resizable;
    }

    @Override
    public void resizable(boolean on) {
        resizable = on;
        render(() -> GLFW.glfwSetWindowAttrib(handle(), GLFW.GLFW_RESIZABLE, on ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE));
    }

    @Override
    public boolean focused() {
        return GLFW.glfwGetWindowAttrib(handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
    }

    @Override
    public boolean minimized() {
        return GLFW.glfwGetWindowAttrib(handle(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
    }

    @Override
    public double opacity() {
        return opacity;
    }

    @Override
    public void opacity(double value) {
        opacity = value;
        render(() -> GLFW.glfwSetWindowOpacity(handle(), (float) value));
    }

    @Override
    public String cursor() {
        return cursor;
    }

    @Override
    public void cursor(String value) {
        if (!CURSORS.containsKey(value) && !value.startsWith(Res.SCHEME)) {
            throw new IllegalArgumentException("'" + value + "' is not a cursor, expected one of " + String.join(", ", CURSORS.keySet()) + " or a res:// image");
        }
        cursor = value;
        render(() -> {
            long previous = cursorHandle;
            cursorHandle = value.startsWith(Res.SCHEME) ? imageCursor(value) : GLFW.glfwCreateStandardCursor(CURSORS.get(value));
            GLFW.glfwSetCursor(handle(), value.equals("arrow") ? 0 : cursorHandle);
            if (previous != 0) GLFW.glfwDestroyCursor(previous);
        });
    }

    public void hover(@Nullable String over) {
        String wanted = over == null || CURSORS.containsKey(over) || over.startsWith(Res.SCHEME) ? over : "hand";
        if (Objects.equals(wanted, hovering)) return;
        hovering = wanted;
        long previous = hoverHandle;
        if (wanted == null) {
            hoverHandle = 0;
            GLFW.glfwSetCursor(handle(), cursor.equals("arrow") ? 0 : cursorHandle);
        } else {
            hoverHandle = wanted.startsWith(Res.SCHEME) ? imageCursor(wanted) : GLFW.glfwCreateStandardCursor(CURSORS.get(wanted));
            GLFW.glfwSetCursor(handle(), hoverHandle);
        }
        if (previous != 0) GLFW.glfwDestroyCursor(previous);
    }

    private static long imageCursor(String res) {
        try {
            byte[] bytes = Files.readAllBytes(PlaceToml.root().resolve(Res.parse(res)));
            ByteBuffer encoded = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, w, h, channels, 4);
                if (pixels == null) throw new IOException(STBImage.stbi_failure_reason());
                try {
                    GLFWImage image = GLFWImage.malloc(stack).width(w.get(0)).height(h.get(0)).pixels(pixels);
                    return GLFW.glfwCreateCursor(image, 0, 0);
                } finally {
                    STBImage.stbi_image_free(pixels);
                }
            }
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.warn("could not use {} as the cursor: {}", res, e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean cursorVisible() {
        return cursorVisible;
    }

    @Override
    public void cursorVisible(boolean on) {
        cursorVisible = on;
        render(() -> {
            if (Minecraft.getInstance().mouseHandler.isMouseGrabbed()) return;
            GLFW.glfwSetInputMode(handle(), GLFW.GLFW_CURSOR, on ? GLFW.GLFW_CURSOR_NORMAL : GLFW.GLFW_CURSOR_HIDDEN);
        });
    }

    @Override
    public int fps() {
        return Minecraft.getInstance().getFps();
    }

    @Override
    public int displayWidth() {
        GLFWVidMode mode = mode(monitorOf());
        return mode == null ? 0 : mode.width();
    }

    @Override
    public int displayHeight() {
        GLFWVidMode mode = mode(monitorOf());
        return mode == null ? 0 : mode.height();
    }

    private static @Nullable GLFWVidMode mode(long monitor) {
        return monitor == 0 ? null : GLFW.glfwGetVideoMode(monitor);
    }

    private long monitorOf() {
        long fullscreen = GLFW.glfwGetWindowMonitor(handle());
        if (fullscreen != 0) return fullscreen;
        int cx = x() + width() / 2;
        int cy = y() + height() / 2;
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (monitors != null) {
            for (int n = 0; n < monitors.limit(); n++) {
                long monitor = monitors.get(n);
                GLFWVidMode mode = mode(monitor);
                int[] mx = new int[1];
                int[] my = new int[1];
                GLFW.glfwGetMonitorPos(monitor, mx, my);
                if (mode != null && cx >= mx[0] && cy >= my[0] && cx < mx[0] + mode.width() && cy < my[0] + mode.height()) return monitor;
            }
        }
        return GLFW.glfwGetPrimaryMonitor();
    }

    @Override
    public List<Map<String, Object>> monitors() {
        List<Map<String, Object>> list = new ArrayList<>();
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (monitors == null) return list;
        long primary = GLFW.glfwGetPrimaryMonitor();
        for (int n = 0; n < monitors.limit(); n++) {
            long monitor = monitors.get(n);
            GLFWVidMode mode = mode(monitor);
            int[] mx = new int[1];
            int[] my = new int[1];
            int[] wx = new int[1];
            int[] wy = new int[1];
            int[] ww = new int[1];
            int[] wh = new int[1];
            GLFW.glfwGetMonitorPos(monitor, mx, my);
            GLFW.glfwGetMonitorWorkarea(monitor, wx, wy, ww, wh);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", String.valueOf(GLFW.glfwGetMonitorName(monitor)));
            info.put("x", (double) mx[0]);
            info.put("y", (double) my[0]);
            info.put("width", (double) (mode == null ? 0 : mode.width()));
            info.put("height", (double) (mode == null ? 0 : mode.height()));
            info.put("workX", (double) wx[0]);
            info.put("workY", (double) wy[0]);
            info.put("workWidth", (double) ww[0]);
            info.put("workHeight", (double) wh[0]);
            info.put("refreshRate", (double) (mode == null ? 0 : mode.refreshRate()));
            info.put("primary", monitor == primary);
            list.add(info);
        }
        return list;
    }

    @Override
    public void flash() {
        render(() -> GLFW.glfwRequestWindowAttention(handle()));
    }

    @Override
    public void clipboard(String text) {
        render(() -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
    }

    @Override
    public void preventClose() {
        prevented = true;
    }

    @Override
    public boolean visible() {
        return GLFW.glfwGetWindowAttrib(handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE;
    }

    @Override
    public void visible(boolean on) {
        render(() -> {
            if (on) GLFW.glfwShowWindow(handle());
            else if (Windows.allowed()) GLFW.glfwHideWindow(handle());
            else MoudMod.LOG.warn("the game window can only be hidden in single player and exported games");
        });
    }
}
