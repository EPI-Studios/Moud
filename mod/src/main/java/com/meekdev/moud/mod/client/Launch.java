package com.meekdev.moud.mod.client;

import com.meekdev.moud.mod.features.Feature;
import com.meekdev.moud.mod.features.Features;
import com.meekdev.moud.mod.server.VoidLevel;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.levelgen.WorldOptions;

public final class Launch {

    private final Features features;
    private boolean started;

    public Launch(Features features) {
        this.features = features;
    }

    public void install() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (started || features.isOn(Feature.TITLE_SCREEN)) return;
        if (!(client.screen instanceof TitleScreen)) return;
        started = true;
        open(client);
    }

    private void open(Minecraft client) {
        client.createWorldOpenFlows().createFreshLevel(
                VoidLevel.NAME,
                VoidLevel.settings(),
                WorldOptions.defaultWithRandomSeed(),
                VoidLevel::dimensions,
                new TitleScreen());
    }
}
