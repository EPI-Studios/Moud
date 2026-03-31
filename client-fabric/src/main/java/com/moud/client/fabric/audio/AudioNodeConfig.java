package com.moud.client.fabric.audio;

import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

record AudioNodeConfig(
        long nodeId,
        boolean positional,
        String soundRef,
        Identifier soundId,
        SoundCategory category,
        boolean playing,
        boolean loop,
        float volume,
        float pitch
) {
}
