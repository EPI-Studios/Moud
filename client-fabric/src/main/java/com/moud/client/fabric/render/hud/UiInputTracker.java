package com.moud.client.fabric.render.hud;

import com.moud.client.fabric.runtime.PlayRuntimeClient;
import com.moud.net.protocol.UiNodeEvent;
import com.moud.net.session.Session;
import com.moud.net.transport.Lane;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;

public final class UiInputTracker {

    private static final Set<String> INTERACTIVE_TYPES = Set.of(
            "Button", "TextureButton", "CheckBox", "HSlider", "VSlider", "LineEdit");

    private boolean prevLeftDown;

    public UiInputTracker() {}

    public static boolean isInteractive(String type) {
        return INTERACTIVE_TYPES.contains(type);
    }

    public void tick(Session session, PlayRuntimeClient runtime) {
        if (session == null) return;
        if (runtime == null || !runtime.isCursorModeEnabled()) {
            prevLeftDown = false;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;

        if (client.currentScreen != null) {
            prevLeftDown = false;
            return;
        }
        if (client.mouse.isCursorLocked()) {
            prevLeftDown = false;
            return;
        }

        long handle = client.getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (leftDown && !prevLeftDown) {
            onLeftClick(session, client, handle);
        }

        prevLeftDown = leftDown;
    }

    private void onLeftClick(Session session, MinecraftClient client, long handle) {
        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(handle, mx, my);

        int guiX = (int) mx[0];
        int guiY = (int) my[0];

        List<HudCanvasRenderer.HitResult> hits = HudCanvasRenderer.lastHitResults;
        for (HudCanvasRenderer.HitResult hit : hits) {
            if (!hit.contains(guiX, guiY)) continue;
            UiNodeEvent event = buildEvent(hit);
            if (event != null) session.send(Lane.INPUT, event);
            break; // Topmost hit only
        }
    }

    private static UiNodeEvent buildEvent(HudCanvasRenderer.HitResult hit) {
        return switch (hit.type()) {
            case "Button", "TextureButton" -> new UiNodeEvent(hit.nodeId(), "pressed", 0f);
            case "CheckBox"                -> new UiNodeEvent(hit.nodeId(), "toggled",  0f);
            case "HSlider", "VSlider"      -> new UiNodeEvent(hit.nodeId(), "pressed",  0f);
            case "LineEdit"                -> new UiNodeEvent(hit.nodeId(), "pressed",  0f);
            default                        -> null;
        };
    }
}
