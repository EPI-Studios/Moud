package com.moud.plugin.api.ui;

import com.moud.plugin.api.entity.Player;

@FunctionalInterface
public interface UIOverlayInteractionListener {
    void onInteraction(Player player, UIOverlayInteraction interaction);
}
