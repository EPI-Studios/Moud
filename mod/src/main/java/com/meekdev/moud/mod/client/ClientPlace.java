package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Camera;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.render.Cameras;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.script.vm.Vm;
import org.jspecify.annotations.Nullable;

// the client's own place: client/main.luau, the camera it draws through, and the input it reads
//
// it cannot start until the mirror has a world to hang local instances off, which is after the
// first baseline. so it is started from the tick that finds one rather than from client init
public final class ClientPlace {

    private static final Clock FRAME = new Clock();

    private static @Nullable Place place;
    private static @Nullable Camera camera;
    private static final Input INPUT = new Input();

    private ClientPlace() {}

    public static @Nullable Camera camera() {
        return camera;
    }

    public static void tick() {
        Instance world = ClientScene.world();
        if (world == null) {
            stop();
            return;
        }
        if (place == null) start(world);
        if (place != null) place.pollReload();
    }

    // 7.4 in order: input first, so everything the frame does reads one answer, then the scripts,
    // then the camera the adapters draw through
    public static void frame(float partialTick) {
        if (place == null || camera == null) return;
        INPUT.poll();
        Vm vm = place.vm();
        if (vm != null) vm.renderStep(FRAME.tick());
        Cameras.frame(camera, partialTick);
    }

    // the camera is made per load, not per start: a reload destroys the local instances and a
    // global left pointing at the old one errors the moment the place touches it
    private static void start(Instance world) {
        place = Place.client(world, Classes.registry(), vm -> {
            camera = Instances.createLocal(Classes.CAMERA, world, "Camera");
            vm.bindClient(camera, INPUT);
        });
        place.start();
        MoudMod.LOG.info("the client place is running");
    }

    private static void stop() {
        if (place == null) return;
        place = null;
        camera = null;
        Cameras.release();
    }
}
