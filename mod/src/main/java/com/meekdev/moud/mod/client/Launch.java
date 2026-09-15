package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.client.editor.Editor;
import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.features.Features;
import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.mod.server.VoidLevel;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.levelgen.WorldOptions;

public final class Launch {

    private static Features features;
    private boolean started;

    public Launch(Features features) {
        Launch.features = features;
    }

    public void install() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (!(client.screen instanceof TitleScreen)) return;
        if (!PlaceToml.chosenAtLaunch() && Editor.available()) {
            client.setScreen(Editor.projectHub());
            return;
        }
        if (started || features.isOn(Feature.TITLE_SCREEN)) return;
        started = true;
        open(client);
    }

    public static void openProject(Path root) {
        PlaceToml.open(root, true, features);
        open(Minecraft.getInstance());
    }

    private static void open(Minecraft client) {
        client.createWorldOpenFlows().createFreshLevel(
                VoidLevel.NAME,
                VoidLevel.settings(),
                WorldOptions.defaultWithRandomSeed(),
                VoidLevel::dimensions,
                new TitleScreen());
    }
}
