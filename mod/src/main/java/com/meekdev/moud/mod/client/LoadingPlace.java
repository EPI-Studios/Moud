package com.meekdev.moud.mod.client;

import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.InstanceTree;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.time.Clock;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.audio.ResonaAudio;
import com.meekdev.moud.mod.adapter.audio.Sounds;
import com.meekdev.moud.mod.adapter.ui.LoadingGui;
import com.meekdev.moud.mod.adapter.video.Videos;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.client.screens.PlaceMessageScreen;
import com.meekdev.moud.mod.place.Errors;
import com.meekdev.moud.mod.place.Languages;
import com.meekdev.moud.mod.place.Output;
import com.meekdev.moud.mod.place.PlaceFileRef;
import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.script.engine.PlaceModules;
import com.meekdev.moud.script.engine.ScriptLanguage;
import com.meekdev.moud.script.host.Host;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class LoadingPlace {

    private static final double TICK = 0.05;
    private static final double FADE_SECONDS = 0.4;
    private static final Clock FRAME = new Clock();

    private static @Nullable Host host;
    private static @Nullable Instance world;
    private static boolean finishing;
    private static double fade = 1;

    private LoadingPlace() {}

    public static void install() {
        Pipeline.add(RenderStage.AFTER_GUI, 100, "moud loading script", context -> draw());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LoadingState.tick(client);
            if (host != null) host.step(TICK);
        });
        ClientLoginConnectionEvents.INIT.register((handler, client) -> client.execute(() -> LoadingState.phase(LoadingState.JOINING)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> LoadingState.phase(LoadingState.WORLD)));
    }

    public static boolean showing() {
        return host != null;
    }

    public static @Nullable InstanceTree tree() {
        return host == null || world == null ? null : world.tree();
    }

    public static List<InstanceTree> withLoading(@Nullable InstanceTree tree) {
        List<InstanceTree> trees = new ArrayList<>(2);
        if (tree != null) trees.add(tree);
        InstanceTree loading = tree();
        if (loading != null) trees.add(loading);
        return trees;
    }

    public static void start() {
        if (host != null) {
            finishing = false;
            fade = 1;
            return;
        }
        if (PlaceToml.editOnStart() || !PlaceToml.opened()) return;
        Path root = PlaceToml.root();
        String stem = Res.script(PlaceToml.config().loadingScript());
        int dot = stem.lastIndexOf('.');
        if (dot > stem.lastIndexOf('/')) stem = stem.substring(0, dot);
        for (ScriptLanguage language : Languages.all()) {
            for (String extension : language.extensions()) {
                String file = stem + "." + extension;
                if (Files.isRegularFile(root.resolve(file))) {
                    run(root, language, file);
                    return;
                }
            }
        }
    }

    private static void run(Path root, ScriptLanguage language, String file) {
        String source;
        try {
            source = Files.readString(root.resolve(file));
        } catch (IOException e) {
            MoudMod.LOG.error("could not read {}", file, e);
            return;
        }
        Instance fresh = Instances.createRoot(new InstanceTree(), Classes.SPATIAL, "World");
        Host made = new Host(fresh, Addons.classes(), true)
                .modules(new PlaceModules(root, true))
                .files(new PlaceFileRef(root))
                .audio(ResonaAudio.INSTANCE)
                .window(WindowApi.INSTANCE)
                .loading(LoadingApi.SCRIPT)
                .onError(error -> {
                    Errors.record(error);
                    Output.add(Output.Level.ERROR, "loading", error.getMessage());
                })
                .onPrint(line -> {
                    MoudMod.LOG.info("[loading] {}", line);
                    Output.add(Output.Level.INFO, "loading", line);
                });
        world = fresh;
        host = made;
        finishing = false;
        fade = 1;
        FRAME.tick();
        try {
            made.start(language);
            made.engine().run(file, source);
            made.runScripts();
            MoudMod.LOG.info("the loading script is running");
        } catch (RuntimeException e) {
            MoudMod.LOG.error("{} failed", file, e);
            stop();
        }
    }

    public static void finish() {
        if (host != null) finishing = true;
    }

    public static void frame() {
        Host running = host;
        if (running == null || world == null) return;
        double dt = FRAME.tick();
        running.renderStep(dt);
        if (host == null || world == null) return;
        if (Minecraft.getInstance().level == null) {
            List<InstanceTree> trees = List.of(world.tree());
            Sounds.frame(trees, 0);
            Videos.frame(trees, 0);
        }
        if (!finishing) return;
        fade -= dt / FADE_SECONDS;
        if (fade <= 0) stop();
    }

    private static void draw() {
        Minecraft client = Minecraft.getInstance();
        if (host == null || world == null || client.getOverlay() != null || client.screen instanceof PlaceMessageScreen) {
            LoadingGui.hide();
            return;
        }
        LoadingGui.draw(world.tree(), (float) Math.max(0, fade));
    }

    private static void stop() {
        Host closing = host;
        Instance root = world;
        host = null;
        world = null;
        finishing = false;
        fade = 1;
        LoadingGui.clear();
        if (closing != null) closing.close();
        if (root != null) {
            for (Instance child : List.copyOf(root.children())) Instances.destroy(child);
        }
        if (Minecraft.getInstance().level == null) {
            Sounds.frame(List.of(), 0);
            Videos.frame(List.of(), 0);
        }
        MoudMod.LOG.info("the loading script stopped");
    }
}
