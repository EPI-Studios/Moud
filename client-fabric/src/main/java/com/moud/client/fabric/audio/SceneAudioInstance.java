package com.moud.client.fabric.audio;

import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.net.protocol.SceneSnapshot;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;

abstract class SceneAudioInstance extends AbstractSoundInstance implements TickableSoundInstance {
    protected AudioNodeConfig config;
    protected boolean done;

    protected SceneAudioInstance(AudioNodeConfig config) {
        super(config.soundId(), config.category(), SoundInstance.createRandom());
        this.config = Objects.requireNonNull(config, "config");
        this.volume = config.volume();
        this.pitch = config.pitch();
        this.repeat = config.loop();
        this.repeatDelay = 0;
    }

    protected boolean isNodeStillPlaying() {
        SceneSnapshot.NodeSnapshot node = ClientSceneBus.getNode(config.nodeId());
        return node != null && boolProp(node, "playing", true);
    }

    boolean matches(AudioNodeConfig other) {
        return config.matchesIdentity(other);
    }

    void updateConfig(AudioNodeConfig cfg) {
        this.config = cfg;
        this.volume = cfg.volume();
        this.pitch = cfg.pitch();
    }

    @Override
    public boolean isDone() {
        return done;
    }

    private static boolean boolProp(SceneSnapshot.NodeSnapshot node, String key, boolean fallback) {
        String value = stringProp(node, key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String s = value.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true", "1", "t", "yes", "y" -> true;
            case "false", "0", "f", "no", "n" -> false;
            default -> fallback;
        };
    }

    private static String stringProp(SceneSnapshot.NodeSnapshot node, String key) {
        if (node == null || key == null || key.isBlank() || node.properties() == null) {
            return null;
        }
        for (SceneSnapshot.Property property : node.properties()) {
            if (property != null && key.equals(property.key())) {
                return property.value();
            }
        }
        return null;
    }
}
