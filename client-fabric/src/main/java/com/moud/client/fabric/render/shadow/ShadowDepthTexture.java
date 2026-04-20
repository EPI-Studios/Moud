package com.moud.client.fabric.render.shadow;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;

public final class ShadowDepthTexture extends AbstractTexture {

    private final int slot;

    public ShadowDepthTexture(int slot) {
        this.slot = slot;
    }

    @Override
    public int getGlId() {
        int id = ShadowMaps.atlasDepthTex();
        this.glId = id;
        return id;
    }

    @Override
    public void load(ResourceManager manager) {
    }

    @Override
    public void close() {
    }

    @Override
    public void clearGlId() {
    }
}
