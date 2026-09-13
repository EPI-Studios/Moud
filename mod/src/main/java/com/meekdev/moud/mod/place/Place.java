package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.core.scene.Scene;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.adapter.chat.ServerChat;
import com.meekdev.moud.mod.adapter.physics.BlockRays;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.server.ServerHistory;
import com.meekdev.moud.mod.server.ServerScene;
import com.meekdev.moud.mod.server.debug.ServerDebug;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.script.engine.PlaceModules;
import com.meekdev.moud.script.engine.ScriptLanguage;
import com.meekdev.moud.script.host.Host;
import com.meekdev.moud.script.reload.Watcher;
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
    private final Consumer<Host> extend;
    private @Nullable Host host;
    private @Nullable ScriptLanguage language;
    private @Nullable Watcher watcher;

    private Place(Instance world, ClassRegistry classes, String main, boolean client,
                  Predicate<Instance> dropped, Consumer<Host> extend) {
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

    public static Place client(Instance world, ClassRegistry classes, Consumer<Host> extend) {
        return new Place(world, classes, entry(PlaceToml.config().client()), true, instance -> instance.id() < 0, extend);
    }

    private static String entry(String res) {
        String path = Res.script(res);
        int dot = path.lastIndexOf('.');
        return dot <= path.lastIndexOf('/') ? path : path.substring(0, dot);
    }

    public Path root() {
        return root;
    }

    public @Nullable Host host() {
        return host;
    }

    public void start() {
        host = load(Map.of());
        if (host != null && FabricLoader.getInstance().isDevelopmentEnvironment()) {
            types();
            try {
                watcher = new Watcher(root, Languages.extensions());
                MoudMod.LOG.info("watching {}", root);
            } catch (IOException e) {
                MoudMod.LOG.warn("could not watch {}, reload is off", root, e);
            }
        }
    }

    public void close() {
        if (host != null) host.close();
        host = null;
        if (watcher != null) watcher.close();
        watcher = null;
    }

    public boolean pollReload() {
        if (watcher == null || !watcher.take()) return false;
        MoudMod.LOG.info("reloading the place");

        Map<String, Object> carried = host == null ? Map.of() : host.persist();
        if (host != null) host.close();
        host = null;
        for (Instance child : List.copyOf(world.children())) {
            if (dropped.test(child)) Instances.destroy(child);
        }

        host = load(carried);
        if (host != null) host.reloaded();
        return true;
    }

    private void types() {
        if (language == null || host == null) return;
        try {
            language.writeTypes(root, host.api(), classes);
        } catch (IOException e) {
            MoudMod.LOG.warn("failed to write {} type definitions",
                    language.name(), e);
        }
    }

    private @Nullable ScriptLanguage pick() {
        ScriptLanguage found = null;
        for (ScriptLanguage language : Languages.all()) {
            if (language.extensions().stream().noneMatch(extension -> Files.isRegularFile(root.resolve(main + "." + extension)))) continue;
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
                MoudMod.LOG.error("start scene {} from place.toml not found", path);
                return;
            }
            Scene.load(text, world, classes);
        } catch (RuntimeException e) {
            MoudMod.LOG.error("failed to load scene {}: {}", path, e.getMessage());
        }
    }

    private @Nullable Host load(Map<String, Object> carried) {
        if (!client) scene();
        language = pick();
        ScriptLanguage running = language != null ? language : Languages.all().getFirst();
        String file = null;
        String source = null;
        if (language != null) {
            for (String extension : language.extensions()) {
                Path entry = root.resolve(main + "." + extension);
                if (!Files.isRegularFile(entry)) continue;
                file = main + "." + extension;
                try {
                    source = Files.readString(entry);
                } catch (IOException e) {
                    MoudMod.LOG.error("could not read {}", entry, e);
                }
                break;
            }
        }

        Host fresh = new Host(world, classes, client)
                .post(Post.SERVER)
                .blocks(new BlockRays(Physics::level, true))
                .modules(new PlaceModules(root, client))
                .files(new PlaceFileRef(root))
                .onError(Errors::record)
                .onPrint(line -> MoudMod.LOG.info("[{}] {}", client ? "client" : "server", line));
        if (!client) {
            fresh.store(ServerScene.store()).chat(ServerChat.INSTANCE).history(ServerHistory.INSTANCE).debug(ServerDebug.INSTANCE);
        }
        fresh.persist(carried);
        extend.accept(fresh);
        host = fresh;
        try {
            fresh.start(running);
        } catch (RuntimeException e) {
            MoudMod.LOG.error("{} could not start", running.name(), e);
            fresh.close();
            host = null;
            return null;
        }
        if (source != null) {
            try {
                fresh.engine().run(file, source);
            } catch (RuntimeException e) {
                MoudMod.LOG.error("{} failed", root.resolve(file), e);
            }
        }
        fresh.runScripts();
        return fresh;
    }
}
