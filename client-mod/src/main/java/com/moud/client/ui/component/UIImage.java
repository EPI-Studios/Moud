package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UIImage extends UIComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIImage.class);
    private volatile String source;
    private volatile Identifier textureId;

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

        if (textureId != null) {
            context.setShaderColor(1.0f, 1.0f, 1.0f, (float)this.opacity);

            context.drawTexture(
                    textureId,
                    0, 0, 0, 0,
                    (int) getWidth(), (int) getHeight(),
                    (int) getWidth(), (int) getHeight()
            );
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
        try {
            this.textureId = Identifier.tryParse(source);
            if (this.textureId == null) {
                LOGGER.warn("Invalid texture identifier provided for UIImage: {}", source);
            }
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
}