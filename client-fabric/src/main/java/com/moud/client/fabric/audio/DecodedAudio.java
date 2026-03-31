package com.moud.client.fabric.audio;

import net.minecraft.client.sound.StaticSound;

final class DecodedAudio {
    final String path;
    final long version;
    final StaticSound staticSound;

    DecodedAudio(String path, long version, StaticSound staticSound) {
        this.path = path;
        this.version = version;
        this.staticSound = staticSound;
    }

    void close() {
        try {
            staticSound.close();
        } catch (Exception ignored) {
        }
    }
}
