package com.meekdev.moud.mod;

import com.meekdev.moud.mod.adapter.java.GameTypes;
import com.meekdev.moud.mod.adapter.physics.Physics;
import com.meekdev.moud.mod.addon.Addons;
import com.meekdev.moud.mod.features.Features;
import com.meekdev.moud.mod.level.PlaceChunkGenerator;
import com.meekdev.moud.mod.place.PlaceToml;
import com.meekdev.moud.mod.server.MoudServer;
import com.meekdev.moud.mod.transport.Post;
import com.meekdev.moud.script.mixin.Injections;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MoudMod implements ModInitializer {

    public static final String ID = "moud";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    private static final Features FEATURES = new Features();

    public static Features features() {
        return FEATURES;
    }

    @Override
    public void onInitialize() {
        Addons.install();
        PlaceToml.apply(FEATURES);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR,
                Identifier.fromNamespaceAndPath(ID, "place"), PlaceChunkGenerator.CODEC);
        Post.install();
        MoudServer.install();
        Physics.install();
        GameTypes.install(() -> null, id -> null);
        Injections.prepare();
        LOG.info("moud {} starting", version());
    }

    static String version() {
        return FabricLoader.getInstance()
                .getModContainer(ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }
}
