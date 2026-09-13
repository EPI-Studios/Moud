package com.meekdev.moud.mod.addon;

import com.meekdev.moud.core.addon.Addon;
import com.meekdev.moud.core.clazz.ClassRegistry;
import com.meekdev.moud.core.clazz.Classes;
import com.meekdev.moud.mod.MoudMod;
import com.meekdev.moud.mod.place.Languages;
import com.meekdev.moud.script.engine.ScriptLanguage;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class Addons {

    private static final String ENTRYPOINT = "moud:addon";

    private static List<Addon> loaded = List.of();
    private static ClassRegistry classes;

    private Addons() {}

    public static void install() {
        loaded = List.copyOf(FabricLoader.getInstance().getEntrypoints(ENTRYPOINT, Addon.class));
        classes = Classes.registry(loaded);
        for (Addon addon : loaded) {
            if (addon instanceof LanguageAddon brings) {
                ScriptLanguage language = brings.language();
                Languages.add(language);
                MoudMod.LOG.info("addon {} brought the {} language", addon.id(), language.name());
            }
            MoudMod.LOG.info("addon {} loaded", addon.id());
        }
    }

    public static List<Addon> loaded() {
        return loaded;
    }

    public static ClassRegistry classes() {
        if (classes == null) classes = Classes.registry(loaded);
        return classes;
    }
}
