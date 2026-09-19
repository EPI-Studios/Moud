package com.meekdev.moud.mod.client.editor;

import com.meekdev.amnetic.client.ui.AmneticEditor;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.EditMode;
import com.meekdev.moud.mod.client.Launch;
import com.meekdev.moud.mod.client.editor.document.SceneLink;
import com.meekdev.moud.mod.client.editor.project.ProjectHub;
import com.meekdev.moud.mod.client.editor.project.ProjectHubScreen;
import com.meekdev.moud.mod.client.editor.shell.EditorShell;
import com.meekdev.moud.mod.client.editor.style.EditorFonts;
import com.mojang.blaze3d.platform.InputConstants;
import foundry.imgui.api.ImGuiMCEvents;
import imgui.ImGui;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class Editor {

    private static final String IMGUI = "imguimc";
    private static final String AMNETIC_TOGGLE = "key.amnetic.editor";

    private static KeyMapping toggle;
    private static boolean available;
    private static @Nullable ProjectHub hub;
    private static @Nullable EditorShell shell;
    private static boolean closeRequested;
    private static @Nullable Path reopen;
    private static boolean reopening;
    private static boolean stopHeld;
    private static KeyMapping playtest;
    private static boolean amneticReleased;

    private Editor() {}

    public static void install() {
        if (!FabricLoader.getInstance().isModLoaded(IMGUI)) {
            MoudMod.LOG.info("no imguimc, the editor stays off");
            return;
        }
        AmneticEditor.register(new TreeInspector());
        AmneticEditor.register(new PlaceInspector());
        AmneticEditor.register(new MixinInspector());
        EditMode.install(EditorScreen::new);
        SceneLink.install();
        available = true;
        shell = new EditorShell();
        hub = new ProjectHub();
        ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(hub::render);
        ImGuiMCEvents.INSTANCE.onRegisterImGuiFonts((atlas, defaultFont, scale) -> EditorFonts.register(atlas));
        EditorShell installed = shell;
        ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(installed::render);
        toggle = new KeyMapping("key.moud.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC);
        KeyMappingHelper.registerKeyMapping(toggle);
        playtest = new KeyMapping("key.moud.playtest", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, KeyMapping.Category.MISC);
        KeyMappingHelper.registerKeyMapping(playtest);
        ClientTickEvents.END_CLIENT_TICK.register(Editor::tick);
        MoudMod.LOG.info("editor ready (right shift)");
    }

    static void filesDropped(List<Path> paths) {
        if (shell != null) shell.filesDropped(paths);
    }

    public static boolean available() {
        return available;
    }

    public static void openHub(Minecraft client) {
        client.setScreen(new ProjectHubScreen());
    }

    public static void requestCloseProject() {
        closeRequested = true;
    }

    public static void requestReopenProject(Path root) {
        reopen = root;
    }

    private static void tick(Minecraft client) {
        if (hub != null) hub.tick();
        if (shell != null) shell.tick();
        if (reopen != null && !reopening) {
            reopening = true;
            client.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
            client.setScreen(new TitleScreen());
            return;
        }
        if (reopening && client.level == null) {
            Path root = reopen;
            reopen = null;
            reopening = false;
            MoudMod.LOG.info("reopening {}", root);
            Launch.openProject(root);
            return;
        }
        if (closeRequested) {
            closeRequested = false;
            client.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
            client.setScreen(new ProjectHubScreen());
            return;
        }
        releaseAmneticKey(client);
        handleStop(client);
        boolean pressed = false;
        while (toggle.consumeClick()) pressed = true;
        if (pressed && client.screen == null && !EditMode.editing()) EditMode.request(true);
    }

    private static void handleStop(Minecraft client) {
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(playtest);
        boolean down = key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0 && InputConstants.isKeyDown(client.getWindow(), key.getValue());
        if (down && !stopHeld && EditMode.allowed()) {
            if (!EditMode.editing() && client.screen == null && EditMode.session() > 0) EditMode.request(true);
            else if (EditMode.editing() && !ImGui.getIO().getWantTextInput()) EditMode.request(false);
        }
        stopHeld = down;
    }

    public static String playtestKey() {
        return playtest == null ? "F6" : playtest.getTranslatedKeyMessage().getString();
    }

    private static void releaseAmneticKey(Minecraft client) {
        if (amneticReleased || client.options == null) return;
        amneticReleased = true;
        for (KeyMapping mapping : client.options.keyMappings) {
            if (mapping.getName().equals(AMNETIC_TOGGLE) && mapping.same(toggle)) {
                mapping.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
            }
        }
    }
}
