package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.core.instance.Camera;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.chat.ClientChat;
import com.meekdev.moud.mod.adapter.render.CameraApi;
import com.meekdev.moud.mod.adapter.render.Cameras;
import com.meekdev.moud.mod.adapter.audio.ResonaAudio;
import com.meekdev.moud.mod.adapter.audio.Sounds;
import com.meekdev.moud.mod.adapter.physics.BlockRays;
import net.minecraft.client.Minecraft;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.script.engine.ScriptEngine;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

public final class ClientPlace {

    private static final Clock FRAME = new Clock();

    private static @Nullable Place place;
    private static @Nullable Instance placed;
    private static @Nullable Camera camera;
    private static final Input INPUT = new Input();
    private static final CameraApi LENS = new CameraApi();

    private ClientPlace() {}

    public static @Nullable Path root() {
        return place == null ? null : place.root();
    }

    public static @Nullable ScriptEngine vm() {
        return place == null ? null : place.vm();
    }

    public static @Nullable Camera camera() {
        return camera;
    }

    public static void tick() {
        Instance world = ClientScene.world();
        if (world == null) {
            stop();
            return;
        }
        if (place != null && placed != world) {
            MoudMod.LOG.info("the server replaced its tree, the client place starts again");
            stop();
        }
        if (place == null) start(world);
        if (place != null) place.pollReload();
    }

    public static void frame(float partialTick) {
        if (place == null || camera == null) return;
        INPUT.poll();
        Actions.frame();
        ScriptEngine vm = place.vm();
        if (vm != null) vm.renderStep(FRAME.tick());
        Cameras.frame(camera, partialTick);
    }

    private static void start(Instance world) {
        place = Place.client(world, Addons.classes(), vm -> {
            camera = Instances.createLocal(Classes.CAMERA, world, "Camera");
            vm.bindClient(camera, LENS, INPUT, ClientScene::own);
            vm.bindPost(Post.CLIENT, true);
            vm.bindBlocks(new BlockRays(() -> Minecraft.getInstance().level, false));
            ResonaAudio.INSTANCE.reset();
            Sounds.stopAll();
            vm.bindAudio(ResonaAudio.INSTANCE);
            vm.bindChat(ClientChat.INSTANCE);
            vm.bindDebug(ClientDebug.INSTANCE);
        });
        place.start();
        placed = world;
        MoudMod.LOG.info("the client place is running");
    }

    private static void stop() {
        if (place == null) return;
        place.close();
        place = null;
        placed = null;
        camera = null;
        Cameras.release();
    }
}
