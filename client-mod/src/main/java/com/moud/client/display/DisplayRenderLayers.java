package com.moud.client.display;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

final class DisplayRenderLayers {

    private static final Function<Identifier, RenderLayer> LAYER_CACHE =
            Util.memoize(texture -> RenderLayer.getEntityTranslucent(texture, true));

    private DisplayRenderLayers() {
    }

    static RenderLayer getLayer(Identifier texture) {
        return LAYER_CACHE.apply(texture);
    }
}
