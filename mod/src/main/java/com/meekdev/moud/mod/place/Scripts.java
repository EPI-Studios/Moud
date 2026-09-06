package com.meekdev.moud.mod.place;

import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.instance.Instance;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.script.vm.Vm;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

public final class Scripts {

    private static final String MAIN = "place/server/main.luau";

    private Scripts() {}

    // the place builds its own tree, and until the server side exists it runs where the tree does
    public static @Nullable Vm run(Instance world, ClassRegistry classes) {
        Path path = FabricLoader.getInstance().getGameDir().resolve(MAIN);
        if (!Files.isRegularFile(path)) {
            MoudMod.LOG.info("no {}, nothing to run", path);
            return null;
        }
        String source;
        try {
            source = Files.readString(path);
        } catch (IOException e) {
            MoudMod.LOG.error("could not read {}", path, e);
            return null;
        }

        Vm vm = new Vm();
        vm.bind(world, classes);
        vm.onError(e -> MoudMod.LOG.error("script error", e));
        try {
            vm.run(MAIN, source);
            MoudMod.LOG.info("ran {}", path);
        } catch (RuntimeException e) {
            MoudMod.LOG.error("{} failed", path, e);
        }
        return vm;
    }
}
