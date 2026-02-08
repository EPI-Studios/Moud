package com.moud.client.display;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class DisplayPbrMaterialResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayPbrMaterialResolver.class);

    record PbrTextures(
            Identifier baseColor,
            Identifier normal,
            Identifier metallicRoughness,
            Identifier emissive,
            Identifier occlusion
    ) {}

    private static final Identifier DEFAULT_NORMAL = Identifier.of("moud", "display/pbr_default_normal");
    private static final Identifier DEFAULT_METALLIC_ROUGHNESS = Identifier.of("moud", "display/pbr_default_metallic_roughness");
    private static final Identifier DEFAULT_EMISSIVE = Identifier.of("moud", "display/pbr_default_emissive");
    private static final Identifier DEFAULT_OCCLUSION = Identifier.of("moud", "display/pbr_default_occlusion");

    private static final DisplayPbrMaterialResolver INSTANCE = new DisplayPbrMaterialResolver();

    private final Map<Identifier, Optional<PbrTextures>> cache = new ConcurrentHashMap<>();
    private volatile boolean defaultsRegistered;

    private DisplayPbrMaterialResolver() {
    }

    static DisplayPbrMaterialResolver getInstance() {
        return INSTANCE;
    }

    Optional<PbrTextures> resolve(Identifier baseColor) {
        if (baseColor == null) {
            return Optional.empty();
        }
        if ("moud".equals(baseColor.getNamespace()) && baseColor.getPath().startsWith("display/remote_")) {
            return Optional.empty();
        }
        if (baseColor.getPath().contains("gltf/")) {
            return Optional.empty();
        }
        ensureDefaultsRegistered();
        return cache.computeIfAbsent(baseColor, this::resolveUncached);
    }

    PbrTextures defaultsFor(Identifier baseColor) {
        ensureDefaultsRegistered();
        return new PbrTextures(
                baseColor,
                DEFAULT_NORMAL,
                DEFAULT_METALLIC_ROUGHNESS,
                DEFAULT_EMISSIVE,
                DEFAULT_OCCLUSION
        );
    }

    Identifier defaultNormal() {
        ensureDefaultsRegistered();
        return DEFAULT_NORMAL;
    }

    Identifier defaultMetallicRoughness() {
        ensureDefaultsRegistered();
        return DEFAULT_METALLIC_ROUGHNESS;
    }

    Identifier defaultEmissive() {
        ensureDefaultsRegistered();
        return DEFAULT_EMISSIVE;
    }

    Identifier defaultOcclusion() {
        ensureDefaultsRegistered();
        return DEFAULT_OCCLUSION;
    }

    private Optional<PbrTextures> resolveUncached(Identifier baseColor) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return Optional.empty();
        }
        ResourceManager resourceManager = client.getResourceManager();
        if (resourceManager == null) {
            return Optional.empty();
        }

        Identifier normal = firstExisting(resourceManager, baseColor,
                "_n", "_normal", "_norm");
        Identifier mr = firstExisting(resourceManager, baseColor,
                "_mr", "_metallicroughness", "_metallic_roughness");
        Identifier emissive = firstExisting(resourceManager, baseColor,
                "_e", "_emissive", "_emit");
        Identifier occlusion = firstExisting(resourceManager, baseColor,
                "_ao", "_occlusion", "_occ");

        if (normal == null && mr == null && emissive == null && occlusion == null) {
            return Optional.empty();
        }

        return Optional.of(new PbrTextures(
                baseColor,
                normal != null ? normal : DEFAULT_NORMAL,
                mr != null ? mr : DEFAULT_METALLIC_ROUGHNESS,
                emissive != null ? emissive : DEFAULT_EMISSIVE,
                occlusion != null ? occlusion : DEFAULT_OCCLUSION
        ));
    }

    private static Identifier firstExisting(ResourceManager rm, Identifier base, String... suffixes) {
        if (rm == null || base == null) {
            return null;
        }
        for (String suffix : suffixes) {
            Identifier derived = derive(base, suffix);
            if (derived != null && exists(rm, derived)) {
                return derived;
            }
        }
        return null;
    }

    private static boolean exists(ResourceManager rm, Identifier id) {
        try {
            return rm.getResource(id).isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Identifier derive(Identifier base, String suffix) {
        String path = base.getPath();
        int dot = path.lastIndexOf('.');
        if (dot > 0 && dot > path.lastIndexOf('/')) {
            String root = path.substring(0, dot);
            String ext = path.substring(dot);
            return Identifier.of(base.getNamespace(), root + suffix + ext);
        }
        return Identifier.of(base.getNamespace(), path + suffix + ".png");
    }

    private void ensureDefaultsRegistered() {
        if (defaultsRegistered) {
            return;
        }
        defaultsRegistered = true;

        registerSolidColorTexture(DEFAULT_NORMAL, 128, 128, 255, 255);
        //  metallicRoughness: B=metallic, G=roughness
        registerSolidColorTexture(DEFAULT_METALLIC_ROUGHNESS, 0, 255, 0, 255);
        registerSolidColorTexture(DEFAULT_EMISSIVE, 0, 0, 0, 255);
        registerSolidColorTexture(DEFAULT_OCCLUSION, 255, 255, 255, 255);
    }

    private void registerSolidColorTexture(Identifier id, int r, int g, int b, int a) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        try {
            NativeImage image = new NativeImage(1, 1, true);
            int color = (a & 255) << 24 | (b & 255) << 16 | (g & 255) << 8 | (r & 255);
            image.setColor(0, 0, color);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            client.getTextureManager().registerTexture(id, texture);
            try {
                AbstractTexture t = client.getTextureManager().getTexture(id);
                if (t != null) {
                    t.setFilter(true, false);
                    com.mojang.blaze3d.systems.RenderSystem.bindTexture(t.getGlId());
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            LOGGER.debug("Failed to register display PBR default texture {}", id, t);
        }
    }
}
