package com.meekdev.moud.mod.client.editor;

import com.meekdev.amnetic.client.ui.AmneticEditor;
import com.meekdev.moud.mod.MoudMod;
import net.fabricmc.loader.api.FabricLoader;

public final class Editor {

    private static final String IMGUI = "imguimc";

    private Editor() {}

    public static void install() {
        if (!FabricLoader.getInstance().isModLoaded(IMGUI)) {
            MoudMod.LOG.info("no imguimc, the editor stays off");
            return;
        }
        AmneticEditor.register(new TreeInspector());
        AmneticEditor.register(new PlaceInspector());
        MoudMod.LOG.info("editor ready (right shift)");
    }
}
