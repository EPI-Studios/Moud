package com.moud.plugin.api.services.rendering;

import java.util.Locale;

/**
 * Well-known rendering stages for multi-pass rendering.
 *
 * <p>Stage IDs are forwarded to the client and must match the names produced by Veil's
 * {@code VeilRenderLevelStageEvent.Stage#getName()}.</p>
 */
public enum ClientRenderStage {
    AFTER_SKY,
    AFTER_SOLID_BLOCKS,
    AFTER_CUTOUT_MIPPED_BLOCKS,
    AFTER_CUTOUT_BLOCKS,
    AFTER_ENTITIES,
    AFTER_BLOCK_ENTITIES,
    AFTER_TRANSLUCENT_BLOCKS,
    AFTER_TRIPWIRE_BLOCKS,
    AFTER_PARTICLES,
    AFTER_WEATHER,
    AFTER_LEVEL;

    private final String id;

    ClientRenderStage() {
        this.id = name().toLowerCase(Locale.ROOT);
    }

    public String id() {
        return id;
    }
}
