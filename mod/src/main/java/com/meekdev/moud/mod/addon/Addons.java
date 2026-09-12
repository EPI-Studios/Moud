package com.meekdev.moud.mod.addon;

import com.meekdev.moud.core.addon.Addon;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.mod.MoudMod;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

// who has plugged into the engine, and the one class set they all agreed on
//
// the registry was built four times from four places -- the server, the client's place, the mirror
// and the type writer. four copies of the same fixed list agree by accident; four copies of a list
// somebody else contributed to do not. so it is built once, here, and everything reads this
public final class Addons {

    // the name a third party puts in its own fabric.mod.json to be found
    private static final String ENTRYPOINT = "moud:addon";

    private static List<Addon> loaded = List.of();
    private static ClassRegistry classes;

    private Addons() {}

    // before anything reads the registry, which is before a place starts on either side
    public static void install() {
        loaded = List.copyOf(FabricLoader.getInstance().getEntrypoints(ENTRYPOINT, Addon.class));
        classes = Classes.registry(loaded);
        for (Addon addon : loaded) MoudMod.LOG.info("addon {} loaded", addon.id());
    }

    public static List<Addon> loaded() {
        return loaded;
    }

    // lazily built rather than eagerly, because a static field somewhere else would otherwise
    // have to be initialised in the right order relative to install(), and nothing enforces that
    public static ClassRegistry classes() {
        if (classes == null) classes = Classes.registry(loaded);
        return classes;
    }
}
