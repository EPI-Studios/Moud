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
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

public final class Place {

    private static final String ROOT = "place";
    private static final String MAIN = "server/main.luau";

    private final Instance world;
    private final ClassRegistry classes;
    private final Path root;
    private @Nullable Vm vm;
    private @Nullable Watcher watcher;

    public Place(Instance world, ClassRegistry classes) {
        this.world = world;
        this.classes = classes;
        this.root = FabricLoader.getInstance().getGameDir().resolve(ROOT);
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
    public void pollReload() {
        if (watcher == null || !watcher.take()) return;
        MoudMod.LOG.info("reloading the place");

        Map<String, Object> carried = vm == null ? Map.of() : vm.persist();
        if (vm != null) vm.close();
        for (Instance child : List.copyOf(world.children())) Instances.destroy(child);

        vm = load(carried);
        if (vm != null) vm.reloaded();
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
        Path main = root.resolve(MAIN);
        if (!Files.isRegularFile(main)) {
            MoudMod.LOG.info("no {}, nothing to run", main);
            return null;
        }
        String source;
        try {
            source = Files.readString(main);
        } catch (IOException e) {
            MoudMod.LOG.error("could not read {}", main, e);
            return null;
        }

        Vm fresh = new Vm();
        fresh.bind(world, classes);
        fresh.onError(Errors::record);
        fresh.persist(carried);
        try {
            fresh.run(MAIN, source);
        } catch (RuntimeException e) {
            // a broken edit must not take the client with it, the next save gets another go
            MoudMod.LOG.error("{} failed", main, e);
        }
        return fresh;
    }
}
