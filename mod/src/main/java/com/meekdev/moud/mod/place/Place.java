package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.asset.Res;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.Classes;
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
import com.meekdev.moud.script.host.java.Mixins;
import com.meekdev.moud.script.reload.Watcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
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
    private boolean editing;
    private @Nullable String edited;

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
        if (PlaceToml.editOnStart()) {
            editing = true;
            if (!client) scene();
        } else {
            host = load(Map.of());
        }
        if (!client && (editing || FabricLoader.getInstance().isDevelopmentEnvironment())) types();
        if ((host != null || editing) && FabricLoader.getInstance().isDevelopmentEnvironment()) {
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

    private @Nullable String sceneOnDisk;

    public boolean editing() {
        return editing;
    }

    public void edit() {
        edit(true);
    }

    public void edit(boolean unsaved) {
        if (editing) return;
        editing = true;
        if (host != null) host.close();
        host = null;
        clearWorld();
        if (client) return;
        if (!unsaved) adoptChangedScene();
        if (edited != null) Scene.load(edited, world, classes);
        else scene();
    }

    public void play() {
        play(true);
    }

    public void play(boolean unsaved) {
        if (!editing) return;
        editing = false;
        if (!client) {
            edited = Scene.save(world.children().stream().filter(Place::authored).toList());
            if (!unsaved) adoptChangedScene();
        }
        clearWorld();
        host = load(Map.of());
    }

    private void adoptChangedScene() {
        String path = PlaceToml.config().scene();
        if (path.isEmpty()) return;
        String disk;
        try {
            disk = new PlaceFileRef(root).read(path);
        } catch (RuntimeException e) {
            return;
        }
        if (disk == null || disk.equals(sceneOnDisk)) return;
        sceneOnDisk = disk;
        edited = disk;
        MoudMod.LOG.info("{} changed on disk, using it", path);
        Output.add(Output.Level.SYSTEM, "server", path + " changed on disk, loaded it");
    }

    public static final String DEFAULT_SCENE = "res://scenes/main.scene";

    public String sceneFile() {
        String path = PlaceToml.config().scene();
        return path.isEmpty() ? DEFAULT_SCENE : path;
    }

    public String saveScene() {
        List<Instance> roots = world.children().stream().filter(Place::authored).toList();
        String file = sceneFile();
        String text = Scene.save(roots);
        new PlaceFileRef(root).write(file, text);
        sceneOnDisk = text;
        return file;
    }

    public static boolean authored(Instance instance) {
        for (Instance at = instance; at != null; at = at.parent()) {
            if (at.isA(Classes.CHARACTER)) return false;
        }
        return instance.id() >= 0;
    }

    private void clearWorld() {
        for (Instance child : List.copyOf(world.children())) {
            if (dropped.test(child)) Instances.destroy(child);
        }
    }

    public boolean pollReload() {
        if (watcher == null) return false;
        Set<Path> changes = watcher.changes();
        if (changes.isEmpty()) return false;
        if (editing) return false;
        Path mixins = mixins().toAbsolutePath().normalize();
        List<Path> ours = changes.stream().map(path -> path.toAbsolutePath().normalize()).filter(path -> path.startsWith(mixins)).toList();
        boolean rest = changes.stream().map(path -> path.toAbsolutePath().normalize()).anyMatch(path -> !path.startsWith(mixins) && !isMixin(path));
        if (!rest) {
            if (host == null || ours.isEmpty()) return false;
            for (Path file : ours) mixin(host, file);
            return true;
        }
        MoudMod.LOG.info("reloading the place");
        if (!client) Output.add(Output.Level.SYSTEM, "server", "reloaded the scripts");

        Map<String, Object> carried = host == null ? Map.of() : host.persist();
        if (host != null) host.close();
        host = null;
        clearWorld();

        host = load(carried);
        if (host != null) host.reloaded();
        return true;
    }

    private Path mixins() {
        Path entry = root.resolve(main).getParent();
        return (entry == null ? root : entry).resolve("mixins");
    }

    private static boolean isMixin(Path path) {
        Path parent = path.getParent();
        return parent != null && parent.getFileName() != null && parent.getFileName().toString().equals("mixins");
    }

    private void mixins(Host host) {
        Path dir = mixins();
        if (language == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(this::isScript).sorted().forEach(file -> mixin(host, file));
        } catch (IOException e) {
            MoudMod.LOG.error("could not list {}", dir, e);
        }
    }

    private boolean isScript(Path file) {
        String name = file.getFileName().toString();
        return language != null && language.extensions().stream().anyMatch(extension -> name.endsWith("." + extension) && !name.endsWith(".d." + extension));
    }

    private void mixin(Host host, Path file) {
        String chunk = root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        Mixins.unload(host, chunk);
        if (!Files.isRegularFile(file) || !isScript(file)) {
            MoudMod.LOG.info("took the hooks of {} off", chunk);
            return;
        }
        try {
            String source = Files.readString(file);
            Mixins.loading(host, chunk, () -> host.engine().run(chunk, source));
            MoudMod.LOG.info("mixins in {} are on", chunk);
        } catch (IOException | RuntimeException e) {
            MoudMod.LOG.error("{} failed", chunk, e);
        }
    }

    private void types() {
        if (client) return;
        ScriptLanguage writer = language != null ? language : pick();
        if (writer == null) writer = Languages.all().getFirst();
        try {
            writer.writeTypes(root, Host.describe(classes), classes);
        } catch (IOException e) {
            MoudMod.LOG.warn("failed to write {} type definitions", writer.name(), e);
        } catch (RuntimeException e) {
            MoudMod.LOG.warn("could not describe the script api for {}", writer.name(), e);
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
            sceneOnDisk = text;
            Scene.load(text, world, classes);
        } catch (RuntimeException e) {
            MoudMod.LOG.error("failed to load scene {}: {}", path, e.getMessage());
        }
    }

    private @Nullable Host load(Map<String, Object> carried) {
        if (!client && edited != null) Scene.load(edited, world, classes);
        else if (!client) scene();
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
                .onError(error -> {
                    Errors.record(error);
                    Output.add(Output.Level.ERROR, client ? "client" : "server", error.getMessage());
                })
                .onPrint(line -> {
                    MoudMod.LOG.info("[{}] {}", client ? "client" : "server", line);
                    Output.add(Output.Level.INFO, client ? "client" : "server", line);
                });
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
        mixins(fresh);
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
