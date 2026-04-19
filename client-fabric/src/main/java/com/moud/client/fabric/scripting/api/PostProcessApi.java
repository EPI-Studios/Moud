package com.moud.client.fabric.scripting.api;

import com.moud.client.fabric.render.PostProcessService;

import java.util.HashSet;
import java.util.Set;

public final class PostProcessApi {

    private final Set<String> ownedIds = new HashSet<>();

    public PostProcessApi() {}

    public void register(String id, String fragSrc) {
        if (id == null || id.isEmpty() || fragSrc == null) return;
        PostProcessService.INSTANCE.registerInline(id, fragSrc, 0);
        ownedIds.add(id);
    }

    public void registerPriority(String id, String fragSrc, int priority) {
        if (id == null || id.isEmpty() || fragSrc == null) return;
        PostProcessService.INSTANCE.registerInline(id, fragSrc, priority);
        ownedIds.add(id);
    }

    public void registerShader(String id, String shaderPath) {
        if (id == null || id.isEmpty() || shaderPath == null) return;
        PostProcessService.INSTANCE.registerShader(id, shaderPath, 0);
        ownedIds.add(id);
    }

    public void registerShaderPriority(String id, String shaderPath, int priority) {
        if (id == null || id.isEmpty() || shaderPath == null) return;
        PostProcessService.INSTANCE.registerShader(id, shaderPath, priority);
        ownedIds.add(id);
    }

    public void unregister(String id) {
        if (id == null) return;
        PostProcessService.INSTANCE.unregister(id);
        ownedIds.remove(id);
    }

    public boolean has(String id) {
        if (id == null) return false;
        return PostProcessService.INSTANCE.hasEffect(id);
    }

    public void setUniform1(String id, String key, float v) {
        PostProcessService.INSTANCE.setUniform(id, key, new float[] { v });
    }

    public void setUniform2(String id, String key, float x, float y) {
        PostProcessService.INSTANCE.setUniform(id, key, new float[] { x, y });
    }

    public void setUniform3(String id, String key, float x, float y, float z) {
        PostProcessService.INSTANCE.setUniform(id, key, new float[] { x, y, z });
    }

    public void setUniform4(String id, String key, float x, float y, float z, float w) {
        PostProcessService.INSTANCE.setUniform(id, key, new float[] { x, y, z, w });
    }

    public void setRenderScale(double scale) {
        PostProcessService.INSTANCE.setRenderScale((float) scale);
    }

    public double getRenderScale() {
        return PostProcessService.INSTANCE.getRenderScale();
    }

    public void setUniformTexture(String id, String key, String texturePath) {
        PostProcessService.INSTANCE.setTextureUniform(id, key, texturePath);
    }

    public void disposeOwned() {
        for (String id : ownedIds) {
            PostProcessService.INSTANCE.unregister(id);
        }
        ownedIds.clear();
    }
}
