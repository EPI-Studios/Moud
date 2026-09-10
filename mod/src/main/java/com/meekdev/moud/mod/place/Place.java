package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.core.instance.Instances;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.script.reload.Watcher;
import com.meekdev.moud.script.types.Types;
import com.meekdev.moud.script.vm.Vm;
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

    private static final String ROOT = "place";

    private final Instance world;
    private final ClassRegistry classes;
    private final Path root;
    private final String main;
    private final Predicate<Instance> dropped;
    private final Consumer<Vm> extend;
    private @Nullable Vm vm;
    private @Nullable Watcher watcher;

    private Place(Instance world, ClassRegistry classes, String main,
                  Predicate<Instance> dropped, Consumer<Vm> extend) {
        this.world = world;
        this.classes = classes;
        this.root = FabricLoader.getInstance().getGameDir().resolve(ROOT);
        this.main = main;
        this.dropped = dropped;
        this.extend = extend;
    }

    public static Place server(Instance world, ClassRegistry classes) {
        return new Place(world, classes, "server/main.luau", instance -> true, vm -> { });
    }

    // 8.6: a client reload re-runs the place's local scripts and leaves the mirror alone, so the
    // only things it may destroy are the ones the client made itself. those are the negative ids
    public static Place client(Instance world, ClassRegistry classes, Consumer<Vm> extend) {
        return new Place(world, classes, "client/main.luau", instance -> instance.id() < 0, extend);
    }

    public @Nullable Vm vm() {
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
        if (vm != null) vm.close();
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
        try {
            Types.write(root, classes);
        } catch (IOException e) {
            MoudMod.LOG.warn("could not write the luau definitions, an editor will not know the api", e);
        }
    }

    private @Nullable Vm load(Map<String, Object> carried) {
        Path entry = root.resolve(main);
        if (!Files.isRegularFile(entry)) {
            MoudMod.LOG.info("no {}, nothing to run", entry);
            return null;
        }
        String source;
        try {
            source = Files.readString(entry);
        } catch (IOException e) {
            MoudMod.LOG.error("could not read {}", entry, e);
            return null;
        }

        Vm fresh = new Vm();
        fresh.bind(world, classes);
        fresh.onError(Errors::record);
        fresh.persist(carried);
        extend.accept(fresh);
        try {
            fresh.run(main, source);
        } catch (RuntimeException e) {
            // a broken edit must not take the client with it, the next save gets another go
            MoudMod.LOG.error("{} failed", entry, e);
        }
        return fresh;
    }
}
