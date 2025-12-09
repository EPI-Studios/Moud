package com.moud.client.ui.component;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moud.client.api.service.UIService;
import com.moud.client.ui.atlas.UITextureAtlasManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;

import net.minecraft.util.Identifier;
import org.graalvm.polyglot.HostAccess;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UIImage extends UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIImage.class);
    private volatile String source;
    private volatile Identifier textureId;
    private volatile UITextureAtlasManager.AtlasRegion atlasRegion;

    public UIImage(String source, UIService service) {
        super("image", service);
        this.setSource(source);
        setBackgroundColor("#00000000");
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible || opacity <= 0.01) {
            return;
        }

        context.getMatrices().push();
        context.getMatrices().translate(screenX, screenY, 0);
        context.getMatrices().scale(scaleX, scaleY, 1.0f);

        Identifier id = this.textureId;
        if (id != null) {
            UITextureAtlasManager.AtlasRegion region = this.atlasRegion;
            if (region == null && (width <= 0 || height <= 0)) {
                context.getMatrices().pop();
                return;
            }

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.setShaderColor(1.0f, 1.0f, 1.0f, (float)this.opacity);

            if (region != null) {
                int offsetX = -region.frameX();
                int offsetY = -region.frameY();
                context.drawTexture(
                        id,
                        offsetX, offsetY,
                        region.u(), region.v(),
                        region.width(), region.height(),
                        region.atlasWidth(), region.atlasHeight()
                );
            } else if (width > 0 && height > 0) {
                context.drawTexture(
                        id,
                        0, 0, 0, 0,
                        (int) getWidth(), (int) getHeight(),
                        (int) getWidth(), (int) getHeight()
                );
            }
            context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        if (borderWidth > 0) {
            int borderCol = parseColor(borderColor, opacity);
            if ((borderCol >>> 24) > 0) {
                for (int i = 0; i < borderWidth; i++) {
                    context.drawBorder(i, i, (int) width - i * 2, (int) height - i * 2, borderCol);
                }
            }
        }

        for (UIComponent child : children) {
            context.getMatrices().push();
            context.getMatrices().translate(-screenX, -screenY, 0);
            child.renderWidget(context, mouseX, mouseY, delta);
            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }

    @HostAccess.Export
    public UIImage setSource(String source) {
        this.source = source;
        this.atlasRegion = null;
        try {
            if (source == null || source.isBlank() || source.endsWith(":")) {
                this.textureId = null;
                return this;
            }
            this.textureId = Identifier.tryParse(source);
            if (this.textureId == null) {
                LOGGER.warn("Invalid texture identifier provided for UIImage: {}", source);
                return this;
            }
            applyLinearFilter(this.textureId);
        } catch (Exception e) {
            LOGGER.error("Failed to parse texture identifier for UIImage: {}", source, e);
            this.textureId = null;
        }
        return this;
    }

    @HostAccess.Export
    public String getSource() {
        return source;
    }

    @HostAccess.Export
    public UIImage setAtlasRegion(String atlasPath, String subTextureName) {
        this.atlasRegion = null;
        this.textureId = null;

        UITextureAtlasManager.AtlasRegion region = UITextureAtlasManager.getInstance()
                .getRegion(atlasPath, subTextureName);
        if (region == null) {
            LOGGER.warn("Unable to set atlas region '{}#{}' - region not found", atlasPath, subTextureName);
            return this;
        }

        this.source = atlasPath + "#" + subTextureName;
        this.atlasRegion = region;
        this.textureId = region.textureId();
        this.width = region.frameWidth();
        this.height = region.frameHeight();
        applyLinearFilter(this.textureId);
        return this;
    }

    private void applyLinearFilter(Identifier id) {
        if (id == null) {
            return;
        }
        try {
            var textureManager = MinecraftClient.getInstance().getTextureManager();
            // Force load if missing
            textureManager.getTexture(id);
            AbstractTexture texture = textureManager.getTexture(id);
            if (texture != null) {
                texture.setFilter(true, false); // linear filtering, no mipmaps
                int glId = texture.getGlId();
                RenderSystem.bindTexture(glId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to apply linear filter to texture {}", id, e);
        }
    }
}
