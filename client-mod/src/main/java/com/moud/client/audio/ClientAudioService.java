package com.moud.client.audio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientAudioService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAudioService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final ClientAudioService INSTANCE = new ClientAudioService();

    private static final int MAX_MANAGED_SOUNDS = 64;

    private final Map<String, ManagedSound> activeSounds = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> crossFadeGroups = new ConcurrentHashMap<>();

    private ClientAudioService() {
    }

    public static ClientAudioService getInstance() {
        return INSTANCE;
    }

    public void handleNetworkEvent(String eventName, String payloadJson) {
        try {
            Map<String, Object> payload = payloadJson == null || payloadJson.isEmpty()
                    ? Map.of()
                    : MAPPER.readValue(payloadJson, MAP_TYPE);

            switch (eventName) {
                case "audio:play" -> handlePlay(payload);
                case "audio:update" -> handleUpdate(payload);
                case "audio:stop" -> handleStop(payload);
                default -> LOGGER.warn("Unknown audio event '{}'", eventName);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to handle audio network event '{}': {}", eventName, payloadJson, e);
        }
    }

    public void tick() {
        if (activeSounds.isEmpty()) {
            return;
        }

        long now = System.nanoTime();
        for (Map.Entry<String, ManagedSound> entry : activeSounds.entrySet()) {
            ManagedSound sound = entry.getValue();
            boolean stillPlaying = sound.tick(now);

            if (!stillPlaying) {
                deregisterSound(entry.getKey(), sound);
            }
        }
    }

    private void handlePlay(Map<String, Object> payload) {
        String soundId = string(payload, "id");
        if (soundId == null || soundId.isEmpty()) {
            LOGGER.warn("audio:play missing 'id'");
            return;
        }

        if (activeSounds.size() >= MAX_MANAGED_SOUNDS && !activeSounds.containsKey(soundId)) {
            LOGGER.warn("audio:play ignored '{}': managed sound capacity ({}) reached", soundId, MAX_MANAGED_SOUNDS);
            return;
        }

        ManagedSoundOptions options = ManagedSoundOptions.fromPayload(payload);
        if (options == null) {
            LOGGER.warn("audio:play '{}': failed to parse options", soundId);
            return;
        }

        Identifier soundEventId = options.soundEventId();
        if (soundEventId == null) {
            LOGGER.warn("audio:play '{}' did not specify a sound id", soundId);
            return;
        }
        if (!Registries.SOUND_EVENT.containsId(soundEventId)) {
            LOGGER.warn("audio:play '{}' references unknown sound '{}'", soundId, soundEventId);
            return;
        }

        if (options.crossFadeGroup() != null) {
            fadeOutGroupMembers(soundId, options.crossFadeGroup(), options.crossFadeDurationMs());
        }

        ManagedSound managed = new ManagedSound(soundId, options);
        ManagedSound previous = activeSounds.put(soundId, managed);
        if (previous != null) {
            deregisterSound(soundId, previous);
        }

        registerSoundToGroup(soundId, options.crossFadeGroup());
        queuePlay(managed);
    }

    private void handleUpdate(Map<String, Object> payload) {
        String id = string(payload, "id");
        if (id == null) {
            LOGGER.warn("audio:update missing 'id'");
            return;
        }

        ManagedSound sound = activeSounds.get(id);
        if (sound == null) {
            LOGGER.warn("audio:update for unknown sound '{}'", id);
            return;
        }

        ManagedSoundOptions previous = sound.update(ManagedSoundOptions.partialFromPayload(payload));
        ManagedSoundOptions current = sound.getOptions();

        String previousGroup = previous != null ? previous.crossFadeGroup() : null;
        String currentGroup = current.crossFadeGroup();

        if (!Objects.equals(previousGroup, currentGroup)) {
            removeFromGroup(id, previousGroup);
            registerSoundToGroup(id, currentGroup);
        }

        if (currentGroup != null && !Objects.equals(previousGroup, currentGroup)) {
            fadeOutGroupMembers(id, currentGroup, current.crossFadeDurationMs());
        }
    }

    private void handleStop(Map<String, Object> payload) {
        String id = string(payload, "id");
        if (id == null) {
            LOGGER.warn("audio:stop missing 'id'");
            return;
        }

        ManagedSound sound = activeSounds.get(id);
        if (sound == null) {
            LOGGER.debug("audio:stop ignored for unknown id '{}'", id);
            return;
        }

        Long fadeOut = number(payload, "fadeOutMs");
        boolean immediate = booleanValue(payload, "immediate", false);
        sound.requestStop(fadeOut, immediate);
    }

    private void deregisterSound(String id, ManagedSound sound) {
        activeSounds.remove(id);

        String group = sound.getOptions().crossFadeGroup();
        if (group != null) {
            Set<String> members = crossFadeGroups.get(group);
            if (members != null) {
                members.remove(id);
                if (members.isEmpty()) {
                    crossFadeGroups.remove(group);
                }
            }
        }
    }

    private void removeFromGroup(String soundId, @Nullable String groupId) {
        if (groupId == null) {
            return;
        }
        Set<String> members = crossFadeGroups.get(groupId);
        if (members != null) {
            members.remove(soundId);
            if (members.isEmpty()) {
                crossFadeGroups.remove(groupId);
            }
        }
    }

    private void fadeOutGroupMembers(String newSoundId, String groupId, long fadeOutMs) {
        if (groupId == null) {
            return;
        }
        Set<String> members = crossFadeGroups.get(groupId);
        if (members == null || members.isEmpty()) {
            return;
        }

        for (String memberId : Set.copyOf(members)) {
            if (Objects.equals(memberId, newSoundId)) {
                continue;
            }
            ManagedSound sound = activeSounds.get(memberId);
            if (sound != null) {
                sound.requestStop(fadeOutMs, false);
            }
        }
    }

    private void registerSoundToGroup(String soundId, @Nullable String groupId) {
        if (groupId == null || groupId.isEmpty()) {
            return;
        }
        crossFadeGroups.computeIfAbsent(groupId, key -> ConcurrentHashMap.newKeySet()).add(soundId);
    }

    private void queuePlay(ManagedSound sound) {
        MinecraftClient client = MinecraftClient.getInstance();
        SoundManager manager = client.getSoundManager();
        client.execute(() -> {
            SoundInstance instance = sound.createInstance();
            if (instance != null) {
                manager.play(instance);
            }
        });
    }

    private static @Nullable String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof String string ? string : null;
    }

    private static boolean booleanValue(Map<String, Object> payload, String key, boolean fallback) {
        Object value = payload.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return fallback;
    }

    private static @Nullable Long number(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public Collection<ManagedSound> getActiveSounds() {
        return new HashSet<>(activeSounds.values());
    }
}
