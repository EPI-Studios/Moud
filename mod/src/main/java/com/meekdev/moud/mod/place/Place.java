package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.mod.adapter.physics.BlockRays;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.chat.ServerChat;
import com.meekdev.moud.mod.server.ServerHistory;
import com.meekdev.moud.mod.server.ServerDebug;
import com.meekdev.moud.script.engine.PlaceModules;
import com.meekdev.moud.script.reload.Watcher;
import com.meekdev.moud.script.engine.ScriptEngine;
import com.meekdev.moud.script.engine.ScriptLanguage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

public final class Place {

    private final Instance world;
    private final ClassRegistry classes;
    private final Path root;
    private final String main;
    private final boolean client;
    private final Predicate<Instance> dropped;
    private final Consumer<ScriptEngine> extend;
    private @Nullable ScriptEngine vm;
    private @Nullable ScriptLanguage language;
    private @Nullable Watcher watcher;

    private Place(Instance world, ClassRegistry classes, String main, boolean client,
                  Predicate<Instance> dropped, Consumer<ScriptEngine> extend) {
        this.client = client;
        this.world = world;
        this.classes = classes;
        this.root = PlaceToml.root();
        this.main = main;
        this.dropped = dropped;
        this.extend = extend;
    }

    public static Place server(Instance world, ClassRegistry classes) {
        return new Place(world, classes, entry(PlaceToml.config().server()), false, instance -> true, vm -> { });
    }

    // 8.6: a client reload re-runs the place's local scripts and leaves the mirror alone, so the
    // only things it may destroy are the ones the client made itself. those are the negative ids
    public static Place client(Instance world, ClassRegistry classes, Consumer<ScriptEngine> extend) {
        return new Place(world, classes, entry(PlaceToml.config().client()), true, instance -> instance.id() < 0, extend);
    }

    // the entry file without its extension, which is what says which language it is written in
    private static String entry(String res) {
        String path = Res.parse(res);
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(0, dot);
    }

    public Path root() {
        return root;
    }

    public @Nullable ScriptEngine vm() {
        return vm;
    }

    public void start() {
        vm = load(Map.of());
        // no watcher in an exported jar: no cost, no path, nothing to go wrong
        if (vm != null && FabricLoader.getInstance().isDevelopmentEnvironment()) {
            types();
            try {
                watcher = new Watcher(root);
                MoudMod.LOG.info("watching {}", root);
            } catch (IOException e) {
                MoudMod.LOG.warn("could not watch {}, reload is off", root, e);
            }
        }
    }

    // called at one defined point in the frame, never from inside a script
    //
    // says whether it reloaded, because the tree it tore down held the characters of everyone
    // connected and 8.6 has them respawned rather than left without one
    public boolean pollReload() {
        if (watcher == null || !watcher.take()) return false;
        MoudMod.LOG.info("reloading the place");

        Map<String, Object> carried = vm == null ? Map.of() : vm.persist();
        // gone before anything else runs: a script of the next vm calling back into the engine must never be
        // handed this one, whose native state is freed
        if (vm != null) vm.close();
        vm = null;
        for (Instance child : List.copyOf(world.children())) {
            if (dropped.test(child)) Instances.destroy(child);
        }

        vm = load(carried);
        if (vm != null) vm.reloaded();
        return true;
    }

    // the definitions an editor reads the place against, rewritten on every start so they always
    // describe the engine that is about to run it
    private void types() {
        if (language == null) return;
        try {
            language.writeTypes(root, classes);
        } catch (IOException e) {
            MoudMod.LOG.warn("could not write the {} definitions, an editor will not know the api",
                    language.name(), e);
        }
    }

    // which language runs this place is the answer to what its main file is called. a folder with
    // no main file in any known language simply has nothing to run, which is not an error: an
    // empty place still has to be walkable
    private @Nullable ScriptLanguage pick() {
        ScriptLanguage found = null;
        for (ScriptLanguage language : Languages.all()) {
            if (!Files.isRegularFile(root.resolve(main + "." + language.extension()))) continue;
            if (found != null) {
                throw new IllegalStateException("this place has a " + main + " in two languages, "
                        + found.name() + " and " + language.name() + ", and cannot choose");
            }
            found = language;
        }
        return found;
    }

    private void scene() {
        String path = PlaceToml.config().scene();
        if (path.isEmpty()) return;
        try {
            String text = new PlaceFileRef(root).read(path);
            if (text == null) {
                MoudMod.LOG.error("place.toml opens with {}, which is not there", path);
                return;
            }
            Scene.load(text, world, classes);
        } catch (RuntimeException wrong) {
            MoudMod.LOG.error("the scene {} could not be loaded: {}", path, wrong.getMessage());
        }
    }

    private @Nullable ScriptEngine load(Map<String, Object> carried) {
        // the server's opening scene goes in first, so a script can find it and a place with no script still has it
        if (!client) scene();
        language = pick();
        // no main file still gets a vm: the scene and its script instances need one to run in
        ScriptLanguage running = language != null ? language : Languages.all().getFirst();
        String file = main + "." + running.extension();
        Path entry = root.resolve(file);
        String source = null;
        if (language != null) {
            try {
                source = Files.readString(entry);
            } catch (IOException e) {
                MoudMod.LOG.error("could not read {}", entry, e);
            }
        }

        ScriptEngine fresh = running.engine();
        fresh.bind(world, classes);
        // the server's side of a channel, and the server's half of the verbs
        fresh.bindPost(Post.SERVER, false);
        fresh.bindBlocks(new BlockRays(Physics::level, true));
        fresh.bindModules(new PlaceModules(root, client));
        fresh.bindFiles(new PlaceFileRef(root));
        if (!client) fresh.bindStore(ServerScene.store());
        if (!client) fresh.bindChat(ServerChat.INSTANCE);
        if (!client) fresh.bindHistory(ServerHistory.INSTANCE);
        if (!client) fresh.bindDebug(ServerDebug.INSTANCE);
        fresh.onError(Errors::record);
        fresh.persist(carried);
        extend.accept(fresh);
        // the place's vm from here on, since running main calls back into code that asks the place for it
        vm = fresh;
        if (source != null) {
            try {
                fresh.run(file, source);
            } catch (RuntimeException e) {
                // a broken edit must not take the client with it, the next save gets another go
                MoudMod.LOG.error("{} failed", entry, e);
            }
        }
        fresh.runScripts();
        return fresh;
    }
}
