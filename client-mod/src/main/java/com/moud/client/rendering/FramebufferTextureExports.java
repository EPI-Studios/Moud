package com.moud.client.rendering;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DRAW_BUFFER;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_READ_BUFFER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBlitFramebuffer;


public final class FramebufferTextureExports {
    private static final Logger LOGGER = LoggerFactory.getLogger(FramebufferTextureExports.class);
    private static final String EXPORT_PREFIX = "fbo/";

    private static final Map<Identifier, Export> exportsByTexture = new ConcurrentHashMap<>();
    private static final Map<Identifier, Set<Identifier>> exportsByFramebuffer = new ConcurrentHashMap<>();

    private FramebufferTextureExports() {
    }

    public static Identifier getExportTextureId(Identifier framebufferId) {
        return Identifier.of("moud", EXPORT_PREFIX + framebufferId.getNamespace() + "/" + framebufferId.getPath());
    }

    public static void ensureExport(Identifier exportTextureId) {
        Identifier framebufferId = parseFramebufferId(exportTextureId);
        if (framebufferId == null) {
            return;
        }

        exportsByTexture.computeIfAbsent(exportTextureId, id -> {
            FramebufferMirrorTexture texture = new FramebufferMirrorTexture();
            Export export = new Export(framebufferId, exportTextureId, texture);

            exportsByFramebuffer.computeIfAbsent(framebufferId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(exportTextureId);

            registerTexture(exportTextureId, texture);
            return export;
        });
    }

    public static void update(Identifier framebufferId, AdvancedFbo source) {
        if (source == null) {
            return;
        }
        Set<Identifier> exports = exportsByFramebuffer.get(framebufferId);
        if (exports == null || exports.isEmpty()) {
            return;
        }

        for (Identifier exportTextureId : exports) {
            Export export = exportsByTexture.get(exportTextureId);
            if (export != null) {
                try {
                    export.texture().copyFrom(source);
                } catch (Exception e) {
                    LOGGER.debug("Failed to update framebuffer export {}", exportTextureId, e);
                }
            }
        }
    }

    public static void clear() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> {
                TextureManager textureManager = client.getTextureManager();
                if (textureManager != null) {
                    for (Identifier exportTextureId : exportsByTexture.keySet()) {
                        textureManager.destroyTexture(exportTextureId);
                    }
                }
            });
        }

        exportsByTexture.clear();
        exportsByFramebuffer.clear();
    }

    static @Nullable Identifier parseFramebufferId(Identifier exportTextureId) {
        if (exportTextureId == null) {
            return null;
        }
        if (!"moud".equals(exportTextureId.getNamespace())) {
            return null;
        }
        String path = exportTextureId.getPath();
        if (!path.startsWith(EXPORT_PREFIX)) {
            return null;
        }
        String encoded = path.substring(EXPORT_PREFIX.length());
        int slash = encoded.indexOf('/');
        if (slash <= 0 || slash >= encoded.length() - 1) {
            return null;
        }
        String namespace = encoded.substring(0, slash);
        String fbPath = encoded.substring(slash + 1);
        Identifier framebufferId = Identifier.tryParse(namespace + ":" + fbPath);
        if (framebufferId == null) {
            LOGGER.warn(
                    "Invalid framebuffer export id '{}': could not parse framebuffer '{}:{}'",
                    exportTextureId,
                    namespace,
                    fbPath
            );
        }
        return framebufferId;
    }

    private static void registerTexture(Identifier textureId, AbstractTexture texture) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            TextureManager textureManager = client.getTextureManager();
            if (textureManager == null) {
                return;
            }
            textureManager.registerTexture(textureId, texture);
        });
    }

    private record Export(Identifier framebufferId, Identifier textureId, FramebufferMirrorTexture texture) {
    }

    private static final class FramebufferMirrorTexture extends AbstractTexture {
        private int width = -1;
        private int height = -1;
        private int framebufferId = 0;

        private FramebufferMirrorTexture() {
            setFilter(true, false);
        }

        @Override
        public void load(ResourceManager manager) {
        }

        @Override
        public void close() {
            int id = framebufferId;
            framebufferId = 0;
            if (id != 0) {
                RenderSystem.recordRenderCall(() -> GL30C.glDeleteFramebuffers(id));
            }
            super.close();
        }

        void copyFrom(AdvancedFbo source) {
            RenderSystem.assertOnRenderThread();
            if (source == null) {
                return;
            }

            int srcWidth = source.getWidth();
            int srcHeight = source.getHeight();

            int id = getGlId();
            if (id == 0) {
                bindTexture();
                id = getGlId();
            }

            if (width != srcWidth || height != srcHeight) {
                width = srcWidth;
                height = srcHeight;
                TextureUtil.prepareImage(NativeImage.InternalFormat.RGBA, id, 0, width, height);
            }

            if (framebufferId == 0) {
                framebufferId = GL30C.glGenFramebuffers();
            }

            int previousReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
            int previousDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
            int previousReadBuffer = GL11C.glGetInteger(GL_READ_BUFFER);
            int previousDrawBuffer = GL11C.glGetInteger(GL_DRAW_BUFFER);

            try {
                source.bindRead();
                GL11C.glReadBuffer(GL_COLOR_ATTACHMENT0);

                GL30C.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebufferId);
                GL30C.glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, id, 0);
                GL11C.glDrawBuffer(GL_COLOR_ATTACHMENT0);

                // flip vertically the framebuffer output
                glBlitFramebuffer(0, 0, width, height, 0, height, width, 0, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            } finally {
                GL30C.glBindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer);
                GL30C.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
                GL11C.glReadBuffer(previousReadBuffer);
                GL11C.glDrawBuffer(previousDrawBuffer);
            }
        }
    }
}
