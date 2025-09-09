package com.moud.client.api.service;

import net.minecraft.client.MinecraftClient;
import org.graalvm.polyglot.Context;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CursorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CursorService.class);
    private final MinecraftClient client;
    private Context jsContext;
    private boolean cursorMode = false;
    private boolean serverAuthorized = false;

    public CursorService() {
        this.client = MinecraftClient.getInstance();
    }

    public void setContext(Context jsContext) {
        this.jsContext = jsContext;
    }

    public void enableCursor() {
        if (!serverAuthorized) {
            LOGGER.warn("Cannot enable cursor mode - not authorized by server");
            return;
        }

        this.cursorMode = true;
        long window = client.getWindow().getHandle();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        LOGGER.info("Cursor mode enabled");
    }

    public void disableCursor() {
        this.cursorMode = false;
        long window = client.getWindow().getHandle();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        LOGGER.info("Cursor mode disabled");
    }

    public boolean isCursorMode() {
        return cursorMode;
    }

    public void setServerAuthorized(boolean authorized) {
        this.serverAuthorized = authorized;
        if (!authorized && cursorMode) {
            disableCursor();
        }
        LOGGER.info("Server cursor authorization: {}", authorized);
    }

    public boolean isServerAuthorized() {
        return serverAuthorized;
    }

    public void setCursorPosition(double x, double y) {
        if (!cursorMode) return;

        long window = client.getWindow().getHandle();
        GLFW.glfwSetCursorPos(window, x, y);
    }

    public double[] getCursorPosition() {
        long window = client.getWindow().getHandle();
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        GLFW.glfwGetCursorPos(window, xpos, ypos);
        return new double[]{xpos[0], ypos[0]};
    }

    public void cleanUp() {
        if (cursorMode) {
            disableCursor();
        }
        this.jsContext = null;
        this.serverAuthorized = false;
        LOGGER.info("CursorService cleaned up");
    }
}