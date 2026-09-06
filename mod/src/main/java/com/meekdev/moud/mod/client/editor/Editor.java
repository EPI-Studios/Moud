package com.meekdev.moud.mod.client.editor;

import com.meekdev.amnetic.client.ui.AmneticEditor;
import com.meekdev.moud.mod.MoudMod;
import net.fabricmc.loader.api.FabricLoader;

// amnetic already owns the window and the toggle, so a place's panels are just inspectors it adopts
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
        MoudMod.LOG.info("editor panels registered, right shift opens them");
    }
}
