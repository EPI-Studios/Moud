package com.moud.client.fabric.audio;

import com.moud.client.fabric.scene.SceneNodeTransforms;
import org.joml.Vector3f;
import net.minecraft.client.sound.SoundInstance;

final class SceneAudio3DInstance extends SceneAudioInstance {
    private final Vector3f worldPos = new Vector3f();

    SceneAudio3DInstance(AudioNodeConfig config) {
        super(config);
        this.relative = false;
        this.attenuationType = SoundInstance.AttenuationType.LINEAR;
        updateWorldPos();
    }

    @Override
    public void tick() {
        if (!isNodeStillPlaying()) {
            done = true;
            return;
        }
        updateWorldPos();
    }

    private void updateWorldPos() {
        if (!SceneNodeTransforms.tryWorldPosition(config.nodeId(), worldPos)) {
            done = true;
            return;
        }
        this.x = worldPos.x;
        this.y = worldPos.y;
        this.z = worldPos.z;
    }
}
