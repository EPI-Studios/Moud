package com.moud.client.editor.ui;

import com.moud.client.editor.EditorModeManager;
import com.moud.client.editor.scene.SceneSessionManager;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.internal.ImGuiContext;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class EditorImGuiLayer {
    private static final EditorImGuiLayer INSTANCE = new EditorImGuiLayer();
    private static final String INTER_FONT_RESOURCE = "/assets/moud/fonts/Inter.ttf";
    private static final float DEFAULT_FONT_SIZE = 16f;

    private ImGuiImplGlfw glfwBackend;
    private ImGuiImplGl3 gl3Backend;
    private ImGuiContext imguiContext;
    private long windowHandle;
    private boolean initialized;

    private EditorImGuiLayer() {
    }

    public static EditorImGuiLayer getInstance() {
        return INSTANCE;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        windowHandle = client.getWindow().getHandle();

        ImGuiContext previous = ImGui.getCurrentContext();
        imguiContext = ImGui.createContext();
        ImGui.setCurrentContext(imguiContext);
        configureFonts();
        ImGui.getIO().addConfigFlags(ImGuiConfigFlags.DockingEnable);
        ImGui.getStyle().setWindowRounding(0f);
        ImGui.getStyle().setFrameRounding(0f);

        glfwBackend = new ImGuiImplGlfw();
        glfwBackend.init(windowHandle, false);
        ImGuiIO io = ImGui.getIO();
        io.setSetClipboardTextFn(new ImStrConsumer() {
            @Override
            public void accept(String data) {
                GLFW.glfwSetClipboardString(windowHandle, data);
            }
        });
        io.setGetClipboardTextFn(new ImStrSupplier() {
            @Override
            public String get() {
                String s = GLFW.glfwGetClipboardString(windowHandle);
                return s != null ? s : "";
            }
        });

        gl3Backend = new ImGuiImplGl3();
        gl3Backend.init("#version 150");
        imgui.extension.imguizmo.ImGuizmo.setImGuiContext(imguiContext);

        ImGuiTheme.applyIdaEngineTheme();
        EditorIcons.loadIcons();

        initialized = true;
        if (previous != null && !previous.isNotValidPtr()) {
            ImGui.setCurrentContext(previous);
        }
    }

    private void configureFonts() {
        var io = ImGui.getIO();
        io.getFonts().setFreeTypeRenderer(true);
        try (InputStream stream = EditorImGuiLayer.class.getResourceAsStream(INTER_FONT_RESOURCE)) {
            if (stream != null) {
                Path temp = Files.createTempFile("moud-inter", ".ttf");
                Files.copy(stream, temp, StandardCopyOption.REPLACE_EXISTING);
                io.getFonts().addFontFromFileTTF(temp.toAbsolutePath().toString(), DEFAULT_FONT_SIZE);
                temp.toFile().deleteOnExit();
                return;
            }
        } catch (IOException ignored) {
        }
        io.getFonts().addFontDefault();
    }

    public void render() {
        if (!EditorModeManager.getInstance().isActive()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return;
        }
        ensureInitialized();
        if (!initialized) {
            return;
        }

        ImGuiContext previous = ImGui.getCurrentContext();
        ImGui.setCurrentContext(imguiContext);
        glfwBackend.newFrame();
        gl3Backend.newFrame();
        ImGui.newFrame();

        SceneEditorOverlay overlay = SceneEditorOverlay.getInstance();
        overlay.render();

        Matrix4f viewMatrix = new Matrix4f();
        Matrix4f projectionMatrix = new Matrix4f();
        if (WorldViewCapture.copyMatrices(viewMatrix, projectionMatrix)) {
            overlay.renderWorldGizmo(viewMatrix, projectionMatrix);
        }

        ImGui.render();
        gl3Backend.renderDrawData(ImGui.getDrawData());
        ImGui.setCurrentContext(previous);
    }

    public boolean handleKeyEvent(int key, int scancode, int action, int modifiers) {
        if (!EditorModeManager.getInstance().isActive() || !initialized) {
            return false;
        }
        if (inKeyCallback) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return false;
        }
        ImGuiContext previous = ImGui.getCurrentContext();
        ImGui.setCurrentContext(imguiContext);
        inKeyCallback = true;
        glfwBackend.keyCallback(windowHandle, key, scancode, action, modifiers);
        inKeyCallback = false;
        boolean captured = ImGui.getIO().getWantCaptureKeyboard();
        ImGui.setCurrentContext(previous);
        return captured;
    }

    public boolean handleCharEvent(int codePoint) {
        if (!EditorModeManager.getInstance().isActive() || !initialized) {
            return false;
        }
        if (inKeyCallback) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return false;
        }
        ImGuiContext previous = ImGui.getCurrentContext();
        ImGui.setCurrentContext(imguiContext);
        inKeyCallback = true;
        glfwBackend.charCallback(windowHandle, codePoint);
        inKeyCallback = false;
        boolean captured = ImGui.getIO().getWantCaptureKeyboard();
        ImGui.setCurrentContext(previous);
        return captured;
    }

    private boolean inKeyCallback = false;

    public boolean handleMouseButton(int button, int action, int mods) {
        if (!EditorModeManager.getInstance().isActive()) {
            return false;
        }
        if (!initialized) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return false;
        }
        ImGuiContext previous = ImGui.getCurrentContext();
        ImGui.setCurrentContext(imguiContext);
        glfwBackend.mouseButtonCallback(windowHandle, button, action, mods);
        boolean captured = ImGui.getIO().getWantCaptureMouse();
        ImGui.setCurrentContext(previous);
        return captured;
    }

    public boolean handleScroll(double horizontal, double vertical) {
        if (!EditorModeManager.getInstance().isActive() || !initialized) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return false;
        }
        ImGuiContext previous = ImGui.getCurrentContext();
        ImGui.setCurrentContext(imguiContext);
        glfwBackend.scrollCallback(windowHandle, horizontal, vertical);
        boolean captured = ImGui.getIO().getWantCaptureMouse();
        ImGui.setCurrentContext(previous);
        return captured;
    }

    public boolean handleMouseMove(double x, double y) {
        if (!EditorModeManager.getInstance().isActive()) {
            return false;
        }
        if (!initialized) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return false;
        }
        ImGuiContext previous = ImGui.getCurrentContext();
        ImGui.setCurrentContext(imguiContext);
        glfwBackend.cursorPosCallback(windowHandle, x, y);
        boolean captured = ImGui.getIO().getWantCaptureMouse();
        ImGui.setCurrentContext(previous);
        return captured;
    }

    public void resetMouseState() {
        if (!initialized) {
            return;
        }
        double[] posX = new double[1];
        double[] posY = new double[1];
        GLFW.glfwGetCursorPos(windowHandle, posX, posY);
        handleMouseMove(posX[0], posY[0]);
    }

    public void tick() {
        if (!EditorModeManager.getInstance().isActive()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen != null) {
            return;
        }
        SceneSessionManager.getInstance().tick(client);
    }

    public boolean isMouseOverUI() {
        if (!initialized) {
            return false;
        }
        ImGuiContext previous = ImGui.getCurrentContext();
        ImGui.setCurrentContext(imguiContext);
        boolean wantMouse = ImGui.getIO().getWantCaptureMouse();
        ImGui.setCurrentContext(previous);
        return wantMouse;
    }
}
