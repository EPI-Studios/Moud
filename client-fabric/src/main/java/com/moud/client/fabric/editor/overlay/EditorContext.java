package com.moud.client.fabric.editor.overlay;

import com.moud.client.fabric.platform.MinecraftFreeflyCamera;
import java.util.concurrent.atomic.AtomicLong;

public final class EditorContext {
    private final MinecraftFreeflyCamera camera;
    private volatile EditorOverlay overlay;
    private final AtomicLong pendingScrollBits = new AtomicLong();
    private volatile int viewportX, viewportY, viewportW, viewportH;

    private volatile long selectedNodeId;
    private volatile long hoveredNodeId;
    private volatile float mouseViewportNdcX;
    private volatile float mouseViewportNdcY;
    private volatile boolean mouseInViewport;

    public EditorContext(MinecraftFreeflyCamera camera) {
        this.camera = camera;
    }

    public MinecraftFreeflyCamera camera() {
        return camera;
    }

    public EditorOverlay overlay() {
        return overlay;
    }

    public void setOverlay(EditorOverlay overlay) {
        this.overlay = overlay;
    }

    public boolean isActive() {
        EditorOverlay ov = overlay;
        return ov != null && ov.isOpen();
    }

    public void pushScrollY(double dy) {
        long prev, next;
        do {
            prev = pendingScrollBits.get();
            next = Double.doubleToRawLongBits(Double.longBitsToDouble(prev) + dy);
        } while (!pendingScrollBits.compareAndSet(prev, next));
    }

    public float consumeScrollY() {
        long bits = pendingScrollBits.getAndSet(0L);
        return (float) Double.longBitsToDouble(bits);
    }

    public int viewportX() { return viewportX; }
    public int viewportY() { return viewportY; }
    public int viewportW() { return viewportW; }
    public int viewportH() { return viewportH; }

    public void setViewportBounds(int x, int y, int w, int h) {
        viewportX = x;
        viewportY = y;
        viewportW = Math.max(0, w);
        viewportH = Math.max(0, h);
    }

    public void setSelectedNodeId(long id) {
        selectedNodeId = id;
    }

    public long selectedNodeId() {
        return selectedNodeId;
    }

    public void setHoveredNodeId(long id) {
        hoveredNodeId = id;
    }

    public long hoveredNodeId() {
        return hoveredNodeId;
    }

    public void setMouseViewportNdc(float ndcX, float ndcY, boolean inViewport) {
        mouseViewportNdcX = ndcX;
        mouseViewportNdcY = ndcY;
        mouseInViewport = inViewport;
    }

    public float mouseViewportNdcX() {
        return mouseViewportNdcX;
    }

    public float mouseViewportNdcY() {
        return mouseViewportNdcY;
    }

    public boolean isMouseInViewport() {
        return mouseInViewport;
    }

    public boolean isMouseOverViewport(double mx, double my) {
        int w = viewportW;
        int h = viewportH;
        if (w <= 0 || h <= 0) {
            return false;
        }
        return mx >= viewportX && mx < viewportX + w
                && my >= viewportY && my < viewportY + h;
    }
}
