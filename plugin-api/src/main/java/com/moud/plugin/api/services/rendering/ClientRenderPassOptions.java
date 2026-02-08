package com.moud.plugin.api.services.rendering;

/**
 * Marker interface for client-side render pass option records.
 */
public interface ClientRenderPassOptions {
    String type();

    String stage();

    Integer order();

    Boolean enabled();
}

