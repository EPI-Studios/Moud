package com.moud.client.fabric.audio;

import com.moud.client.fabric.assets.MoudAudioAssets;
import com.moud.client.fabric.scene.ClientSceneBus;
import com.moud.client.fabric.util.ClientDebugLog;
import com.moud.net.protocol.SceneSnapshot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

public final class SceneAudioManager {
    private final HashMap<Long, SceneAudioInstance> activeByNodeId = new HashMap<>();
    private final HashMap<Long, CustomAudioSource> customByNodeId = new HashMap<>();
    private final HashSet<Long> consumedOneShots = new HashSet<>();
    private final HashSet<String> warnedUnsupportedPaths = new HashSet<>();
    private final HashMap<String, DecodedAudio> decodedByPath = new HashMap<>();

    public void tick(MinecraftClient client, boolean enabled) {
        if (client == null || client.getSoundManager() == null || !enabled) {
            clear(client);
            return;
        }
        SoundManager soundManager = client.getSoundManager();
        reapFinished(soundManager);
        reapCustomFinished();
        reconcile(client, soundManager);
    }

    public void clear(MinecraftClient client) {
        if (client != null && client.getSoundManager() != null) {
            SoundManager soundManager = client.getSoundManager();
            for (SceneAudioInstance instance : activeByNodeId.values()) {
                if (instance != null) {
                    soundManager.stop(instance);
                }
            }
        }
        activeByNodeId.clear();
        for (CustomAudioSource source : customByNodeId.values()) {
            if (source != null) {
                source.stop();
            }
        }
        customByNodeId.clear();
        for (DecodedAudio decoded : decodedByPath.values()) {
            if (decoded != null) {
                decoded.close();
            }
        }
        decodedByPath.clear();
        consumedOneShots.clear();
    }

    private void reapFinished(SoundManager soundManager) {
        Iterator<Map.Entry<Long, SceneAudioInstance>> it = activeByNodeId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, SceneAudioInstance> entry = it.next();
            SceneAudioInstance instance = entry.getValue();
            if (instance == null) {
                it.remove();
                continue;
            }
            if (instance.isDone() || !soundManager.isPlaying(instance)) {
                it.remove();
            }
        }
    }

    private void reapCustomFinished() {
        Iterator<Map.Entry<Long, CustomAudioSource>> it = customByNodeId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, CustomAudioSource> entry = it.next();
            CustomAudioSource source = entry.getValue();
            if (source == null) {
                it.remove();
                continue;
            }
            if (source.isDone()) {
                source.stop();
                it.remove();
            }
        }
    }

    private void reconcile(MinecraftClient client, SoundManager soundManager) {
        List<SceneSnapshot.NodeSnapshot> nodes = ClientSceneBus.copyNodes();
        HashSet<Long> seen = new HashSet<>();

        for (SceneSnapshot.NodeSnapshot node : nodes) {
            if (node == null || node.nodeId() <= 0L) {
                continue;
            }
            String type = node.type();
            boolean positional = "AudioPlayer3D".equals(type);
            boolean flat = "AudioPlayer2D".equals(type);
            if (!flat && !positional) {
                continue;
            }

            seen.add(node.nodeId());
            AudioNodeConfig cfg = readConfig(node, positional);
            if (cfg == null) {
            }
            String soundRef = cfg == null ? null : cfg.soundRef();
            boolean customAudio = soundRef != null && soundRef.startsWith("res://");
            SceneAudioInstance existing = activeByNodeId.get(node.nodeId());
            CustomAudioSource customExisting = customByNodeId.get(node.nodeId());

            if (cfg == null || !cfg.playing()) {
                if (existing != null) {
                    soundManager.stop(existing);
                    activeByNodeId.remove(node.nodeId());
                }
                if (customExisting != null) {
                    customExisting.stop();
                    customByNodeId.remove(node.nodeId());
                }
                consumedOneShots.remove(node.nodeId());
                continue;
            }

            if (customAudio || positional) {
                if (existing != null) {
                    soundManager.stop(existing);
                    activeByNodeId.remove(node.nodeId());
                }
                reconcileCustom(client, cfg, customExisting);
                continue;
            }

            if (customExisting != null) {
                customExisting.stop();
                customByNodeId.remove(node.nodeId());
            }

            if (existing != null && existing.matches(cfg)) {
                existing.updateConfig(cfg);
                continue;
            }

            if (existing != null) {
                soundManager.stop(existing);
                activeByNodeId.remove(node.nodeId());
            }

            if (!cfg.loop() && consumedOneShots.contains(node.nodeId())) {
                continue;
            }

            SceneAudioInstance next = new SceneAudio2DInstance(cfg);
            activeByNodeId.put(node.nodeId(), next);
            soundManager.play(next);
            if (!cfg.loop()) {
                consumedOneShots.add(node.nodeId());
            }
        }

        Iterator<Map.Entry<Long, SceneAudioInstance>> it = activeByNodeId.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, SceneAudioInstance> entry = it.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            soundManager.stop(entry.getValue());
            it.remove();
            consumedOneShots.remove(entry.getKey());
        }
        Iterator<Map.Entry<Long, CustomAudioSource>> customIt = customByNodeId.entrySet().iterator();
        while (customIt.hasNext()) {
            Map.Entry<Long, CustomAudioSource> entry = customIt.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            entry.getValue().stop();
            customIt.remove();
            consumedOneShots.remove(entry.getKey());
        }
    }

    private void reconcileCustom(MinecraftClient client, AudioNodeConfig cfg, CustomAudioSource existing) {
        if (existing != null && existing.matches(cfg)) {
            existing.updateConfig(cfg);
            existing.tick();
            return;
        }

        if (existing != null) {
            existing.stop();
            customByNodeId.remove(cfg.nodeId());
        }

        if (!cfg.loop() && consumedOneShots.contains(cfg.nodeId())) {
            return;
        }

        DecodedAudio decoded = resolveDecoded(cfg.soundRef());
        if (decoded == null) {
            if (cfg.positional() && client != null && client.getSoundManager() != null) {
                SceneAudioInstance fallback = new SceneAudio3DInstance(cfg);
                activeByNodeId.put(cfg.nodeId(), fallback);
                client.getSoundManager().play(fallback);
                if (!cfg.loop()) consumedOneShots.add(cfg.nodeId());
            }
            return;
        }

        CustomAudioSource next = new CustomAudioSource(client, cfg, decoded);
        next.start();
        customByNodeId.put(cfg.nodeId(), next);
        if (!cfg.loop()) {
            consumedOneShots.add(cfg.nodeId());
        }
    }

    private DecodedAudio resolveDecoded(String soundRef) {
        if (soundRef == null || soundRef.isBlank()) {
            return null;
        }
        long version = MoudAudioAssets.versionOf(soundRef);
        DecodedAudio cached = decodedByPath.get(soundRef);
        if (cached != null && cached.version == version) {
            return cached;
        }
        if (cached != null) {
            cached.close();
            decodedByPath.remove(soundRef);
        }

        byte[] bytes = MoudAudioAssets.readBytes(soundRef);
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            StaticSound staticSound = AudioDecode.decodeStaticSound(soundRef, bytes);
            DecodedAudio decoded = new DecodedAudio(soundRef, version, staticSound);
            decodedByPath.put(soundRef, decoded);
            return decoded;
        } catch (Exception e) {
            if (warnedUnsupportedPaths.add(soundRef + "::decode")) {
                ClientDebugLog.warn("Audio decode failed path=" + soundRef + " error=" + e.getMessage());
            }
            return null;
        }
    }

    private AudioNodeConfig readConfig(SceneSnapshot.NodeSnapshot node, boolean positional) {
        String soundIdRaw = stringProp(node, "sound_id");
        if (soundIdRaw == null || soundIdRaw.isBlank()) {
            return null;
        }
        String trimmed = soundIdRaw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!trimmed.contains("://") && (lower.endsWith(".ogg") || lower.endsWith(".wav") || lower.endsWith(".mp3"))) {
            trimmed = "res://audio/" + trimmed;
        }

        Identifier soundId = trimmed.startsWith("res://") ? Identifier.of("moud", "dynamic/audio") : Identifier.tryParse(trimmed);
        if (soundId == null) {
            return null;
        }

        boolean playing = boolProp(node, "playing", true);
        boolean loop = boolProp(node, "loop", true);
        float volume = dbToGain(floatProp(node, "volume_db", 0.0f));
        float pitch = Math.max(0.01f, floatProp(node, "pitch_scale", 1.0f));
        SoundCategory category = parseCategory(stringProp(node, "category"), positional ? SoundCategory.AMBIENT : SoundCategory.MASTER);
        return new AudioNodeConfig(node.nodeId(), positional, trimmed, soundId, category, playing, loop, volume, pitch);
    }

    private static String stringProp(SceneSnapshot.NodeSnapshot node, String key) {
        List<SceneSnapshot.Property> props = node.properties();
        if (props == null || key == null) {
            return null;
        }
        for (SceneSnapshot.Property prop : props) {
            if (prop != null && key.equals(prop.key())) {
                return prop.value();
            }
        }
        return null;
    }

    private static float floatProp(SceneSnapshot.NodeSnapshot node, String key, float fallback) {
        String value = stringProp(node, key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
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

    private static float dbToGain(float db) {
        return (float) Math.pow(10.0, db / 20.0);
    }

    private static SoundCategory parseCategory(String value, SoundCategory fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return SoundCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
