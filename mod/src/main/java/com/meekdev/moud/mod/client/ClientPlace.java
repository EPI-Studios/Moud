package com.meekdev.moud.mod.client;

import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.render.Camera;
import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.audio.ResonaAudio;
import com.meekdev.moud.mod.adapter.audio.Sounds;
import com.meekdev.moud.mod.adapter.chat.ClientChat;
import com.meekdev.moud.mod.adapter.physics.BlockRays;
import com.meekdev.moud.mod.adapter.render.CameraApi;
import com.meekdev.moud.mod.adapter.render.Cameras;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.client.debug.ClientDebug;
import com.meekdev.moud.mod.client.input.Actions;
import com.meekdev.moud.mod.client.input.Input;
import com.meekdev.moud.mod.place.Place;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.script.host.Host;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class ClientPlace {

    private static final Clock FRAME = new Clock();

    private static @Nullable Place place;
    private static @Nullable Instance placeWorld;
    private static @Nullable Camera camera;
    private static final Input INPUT = new Input();
    private static final CameraApi LENS = new CameraApi();

    private ClientPlace() {}

    public static @Nullable Path root() {
        return place == null ? null : place.root();
    }

    public static @Nullable Host host() {
        return place == null ? null : place.host();
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
        if (place != null && placeWorld != world) {
            MoudMod.LOG.info("server tree replaced, restarting client place");
            stop();
        }
        if (place == null) start(world);
        if (place != null) place.pollReload();
    }

    public static void frame(float partialTick) {
        if (place == null || camera == null) return;
        INPUT.poll();
        Actions.frame();
        Host host = place.host();
        if (host != null) host.renderStep(FRAME.tick());
        Cameras.frame(camera, partialTick);
    }

    private static void start(Instance world) {
        place = Place.client(world, Addons.classes(), host -> {
            camera = Instances.createLocal(Classes.CAMERA, world, "Camera");
            ResonaAudio.INSTANCE.reset();
            Sounds.stopAll();
            host.clientSide(camera, LENS, INPUT, ClientScene::own)
                    .post(Post.CLIENT)
                    .blocks(new BlockRays(() -> Minecraft.getInstance().level, false))
                    .audio(ResonaAudio.INSTANCE)
                    .chat(ClientChat.INSTANCE)
                    .debug(ClientDebug.INSTANCE);
        });
        place.start();
        placeWorld = world;
        MoudMod.LOG.info("the client place is running");
    }

    private static void stop() {
        if (place == null) return;
        place.close();
        place = null;
        placeWorld = null;
        camera = null;
        Cameras.release();
    }
}
