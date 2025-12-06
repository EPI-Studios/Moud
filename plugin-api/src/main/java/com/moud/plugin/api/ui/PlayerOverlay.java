package com.moud.plugin.api.ui;

import java.util.Collection;

public interface PlayerOverlay {
    /**
     * Creates or updates a single overlay component. Returns the component id (generated if not provided).
     */
    String upsert(UIOverlayDefinition definition);

    /**
     * Batch create or update multiple components.
     */
    void upsertMany(Collection<UIOverlayDefinition> definitions);

    /**
     * Removes a component by id.
     */
    void remove(String id);

    /**
     * Removes multiple components by id.
     */
    void removeMany(Collection<String> ids);

    /**
     * Clears all server-driven overlay components for this player.
     */
    void clear();

    /**
     * Registers a callback for UI interactions (click, submit, change, hover, focus, blur) from server-driven overlays.
     * Pass {@code null} to unregister.
     */
    void onInteraction(UIOverlayInteractionListener listener);

    /**
     * Convenience overload for builders.
     */
    default String upsert(OverlayComponentBuilder builder) {
        return builder == null ? null : upsert(builder.build());
    }

    /**
     * Convenience overload for varargs definitions.
     */
    default void upsertMany(UIOverlayDefinition... definitions) {
        if (definitions == null || definitions.length == 0) return;
        java.util.List<UIOverlayDefinition> list = new java.util.ArrayList<>();
        for (UIOverlayDefinition def : definitions) {
            if (def != null) list.add(def);
        }
        if (!list.isEmpty()) {
            upsertMany(list);
        }
    }

    /**
     * Convenience overload for varargs builders.
     */
    default void upsertMany(OverlayComponentBuilder... builders) {
        if (builders == null || builders.length == 0) return;
        java.util.List<UIOverlayDefinition> list = new java.util.ArrayList<>();
        for (OverlayComponentBuilder b : builders) {
            if (b != null) list.add(b.build());
        }
        if (!list.isEmpty()) {
            upsertMany(list);
        }
    }
}
