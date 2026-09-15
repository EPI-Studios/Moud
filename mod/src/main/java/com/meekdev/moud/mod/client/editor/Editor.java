package com.meekdev.moud.mod.client.editor;

import com.meekdev.amnetic.client.ui.AmneticEditor;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.client.editor.document.SceneLink;
import com.meekdev.moud.mod.client.editor.shell.EditorShell;
import com.meekdev.moud.mod.client.editor.style.EditorFonts;
import com.mojang.blaze3d.platform.InputConstants;
import foundry.imgui.api.ImGuiMCEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Editor {

    private static final String IMGUI = "imguimc";
    private static final String AMNETIC_TOGGLE = "key.amnetic.editor";

    private static KeyMapping toggle;
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
        EditMode.install();
        SceneLink.install();
        EditorShell shell = new EditorShell();
        ImGuiMCEvents.INSTANCE.onRegisterImGuiFonts((atlas, defaultFont, scale) -> EditorFonts.register(atlas));
        ImGuiMCEvents.INSTANCE.postRenderImGuiEvent(shell::render);
        toggle = new KeyMapping("key.moud.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC);
        KeyMappingHelper.registerKeyMapping(toggle);
        ClientTickEvents.END_CLIENT_TICK.register(Editor::tick);
        MoudMod.LOG.info("editor ready (right shift)");
    }

    private static void tick(Minecraft client) {
        releaseAmneticKey(client);
        boolean pressed = false;
        while (toggle.consumeClick()) pressed = true;
        if (pressed && client.screen == null && !EditMode.editing()) EditMode.request(true);
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
