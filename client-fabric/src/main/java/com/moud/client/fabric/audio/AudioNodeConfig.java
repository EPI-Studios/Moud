package com.moud.client.fabric.audio;

import java.util.Objects;
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
    boolean matchesIdentity(AudioNodeConfig other) {
        if (other == null) return false;
        return nodeId == other.nodeId
                && positional == other.positional
                && Objects.equals(soundRef, other.soundRef)
                && Objects.equals(soundId, other.soundId)
                && category == other.category
                && loop == other.loop;
    }
}
