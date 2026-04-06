package com.moud.client.fabric.audio;

import com.moud.client.fabric.assets.MoudAudioAssets;
import com.moud.client.fabric.mixin.accessor.SoundManagerAccessor;
import com.moud.client.fabric.mixin.accessor.SoundSystemAccessor;
import com.moud.client.fabric.scene.SceneNodeTransforms;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundEngine;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

final class CustomAudioSource {
    private final MinecraftClient client;
    private AudioNodeConfig config;
    private final DecodedAudio decoded;
    private final Vector3f worldPos = new Vector3f();
    private CompletableFuture<Channel.SourceManager> future;
    private Channel.SourceManager sourceManager;
    private boolean done;
    private boolean stopped;

    CustomAudioSource(MinecraftClient client, AudioNodeConfig config, DecodedAudio decoded) {
        this.client = client;
        this.config = config;
        this.decoded = decoded;
    }

    void start() {
        SoundSystem soundSystem = soundSystem(client);
        if (soundSystem == null) {
            done = true;
            return;
        }
        Channel channel = ((SoundSystemAccessor) soundSystem).moud$getChannel();
        future = channel.createSource(SoundEngine.RunMode.STATIC);
        future.thenAccept(manager -> {
            if (manager == null) {
                done = true;
                return;
            }
            if (stopped) {
                tryClose(manager);
                return;
            }
            sourceManager = manager;
            if (stopped) {
                sourceManager = null;
                tryClose(manager);
                return;
            }
            manager.run(source -> {
                source.setBuffer(decoded.staticSound);
                source.setVolume(config.volume());
                source.setPitch(config.pitch());
                source.setLooping(config.loop());
                if (config.positional()) {
                    updateWorldPos();
                    source.setRelative(false);
                    source.setPosition(new Vec3d(worldPos.x, worldPos.y, worldPos.z));
                    source.setAttenuation(1.0f);
                } else {
                    source.setRelative(true);
                    source.disableAttenuation();
                    source.setPosition(Vec3d.ZERO);
                }
                source.play();
            });
        });
    }

    void tick() {
        if (stopped || done || sourceManager == null) {
            return;
        }
        sourceManager.run(source -> {
            if (stopped) {
                return;
            }
            if (!source.isPlaying() && !config.loop()) {
                done = true;
                return;
            }
            source.setVolume(config.volume());
            source.setPitch(config.pitch());
            if (config.positional()) {
                updateWorldPos();
                source.setPosition(new Vec3d(worldPos.x, worldPos.y, worldPos.z));
            }
        });
    }

    void stop() {
        stopped = true;
        done = true;
        Channel.SourceManager manager = sourceManager;
        sourceManager = null;
        if (manager != null) {
            tryClose(manager);
        }
    }

    private static void tryClose(Channel.SourceManager manager) {
        try {
            manager.close();
        } catch (Exception ignored) {
        }
    }

    boolean matches(AudioNodeConfig other) {
        return config.matchesIdentity(other) && decoded.version == MoudAudioAssets.versionOf(config.soundRef());
    }

    void updateConfig(AudioNodeConfig cfg) {
        this.config = cfg;
    }

    boolean isDone() {
        return done;
    }

    private void updateWorldPos() {
        if (!SceneNodeTransforms.tryWorldPosition(config.nodeId(), worldPos)) {
            done = true;
        }
    }

    private static SoundSystem soundSystem(MinecraftClient client) {
        if (client == null || client.getSoundManager() == null) {
            return null;
        }
        SoundManager soundManager = client.getSoundManager();
        SoundSystem soundSystem = ((SoundManagerAccessor) soundManager).moud$getSoundSystem();
        if (soundSystem == null || !((SoundSystemAccessor) soundSystem).moud$isStarted()) {
            return null;
        }
        return soundSystem;
    }
}
