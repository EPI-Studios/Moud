package com.moud.plugin.api.services;

import com.moud.plugin.api.player.PlayerContext;

public interface RenderingController {
    void applyPostEffect(String effectId);
    void removePostEffect(String effectId);
    void clearPostEffects();
    void toast(PlayerContext player, String title, String body);
}
