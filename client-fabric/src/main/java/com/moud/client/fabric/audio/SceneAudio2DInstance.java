package com.moud.client.fabric.audio;

import net.minecraft.client.sound.SoundInstance;

final class SceneAudio2DInstance extends SceneAudioInstance {
    SceneAudio2DInstance(AudioNodeConfig config) {
        super(config);
        this.relative = true;
        this.attenuationType = SoundInstance.AttenuationType.NONE;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
    }

    @Override
    public void tick() {
        if (!isNodeStillPlaying()) {
            done = true;
        }
    }
}
