package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Parts;
import com.meekdev.moud.mod.adapter.render.Pipeline;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.script.vm.Vm;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public final class MoudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientScene.start();

        Place place = new Place(ClientScene.world(), Classes.registry());
        place.start();
        if (place.vm() != null || Demo.enabled()) Demo.switches(MoudMod.features());
        if (place.vm() == null && Demo.enabled()) Demo.build(ClientScene.world());

        Pipeline.install();
        Parts.register();
        frames(place);
        new Launch(MoudMod.features()).install();
        MoudMod.LOG.info("moud client ready");
    }

    // stepped is the tick, renderStepped is the frame, and both get real time rather than a rate.
    // the drain runs every frame whether or not a script does, because interpolation is what the
    // renderer samples from
    private static void frames(Place place) {
        Clock tick = new Clock();
        Clock frame = new Clock();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // reload lands here, between one script running and the next, never inside one
            place.pollReload();
            Vm vm = place.vm();
            if (vm != null) vm.step(tick.tick());
        });

        LevelRenderEvents.START_MAIN.register(context -> {
            double dt = frame.tick();
            Vm vm = place.vm();
            if (vm != null) vm.renderStep(dt);
            ClientScene.motion().drain(ClientScene.tree(), dt);
        });
    }
}
