package com.meekdev.moud.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MoudMod implements ModInitializer {

    public static final String ID = "moud";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    @Override
    public void onInitialize() {
        LOG.info("moud {} starting", version());
    }

    static String version() {
        return FabricLoader.getInstance()
                .getModContainer(ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }
}
