package com.moud.client.audio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
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
    private final Map<String, DuckState> duckingTargets = new ConcurrentHashMap<>();
    private volatile long lastTickNanos = Long.MIN_VALUE;

    private final Map<Identifier, SoundEvent> dynamicSoundEvents = new ConcurrentHashMap<>();

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
        updateDucking(now);

        for (Map.Entry<String, ManagedSound> entry : activeSounds.entrySet()) {
            ManagedSound sound = entry.getValue();
            sound.setMixVolumeMultiplier(getMixVolumeMultiplier(sound));
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

        if (soundEventId.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            soundEventId = Identifier.of("moud", soundEventId.getPath());

            options = new ManagedSoundOptions(
                    soundEventId,
                    options.categoryRaw(),
                    options.baseVolume(),
                    options.basePitch(),
                    options.loopRaw(),
                    options.startDelayMs(),
                    options.fadeInMs(),
                    options.fadeInEasing(),
                    options.fadeOutMs(),
                    options.fadeOutEasing(),
                    options.positionalRaw(),
                    options.positionRaw(),
                    options.minDistance(),
                    options.maxDistance(),
                    options.distanceModel(),
                    options.rolloff(),
                    options.pitchRamp(),
                    options.volumeLfo(),
                    options.pitchLfo(),
                    options.mixGroup(),
                    options.ducking(),
                    options.crossFadeGroup(),
                    options.crossFadeDurationMs()
            );
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
        if (options.startDelayMs() <= 0L) {
            queuePlay(managed);
        }
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

    public SoundEvent getSoundEvent(Identifier id) {
        return dynamicSoundEvents.computeIfAbsent(id, SoundEvent::of);
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
            SoundInstance instance = sound.createInstance(System.nanoTime());
            if (instance != null) {
                manager.play(instance);
            }
        });
    }

    private void updateDucking(long nowNanos) {
        long dtMs;
        if (lastTickNanos == Long.MIN_VALUE) {
            dtMs = 0L;
        } else {
            dtMs = (nowNanos - lastTickNanos) / 1_000_000L;
            dtMs = Math.max(0L, Math.min(dtMs, 250L));
        }
        lastTickNanos = nowNanos;

        Map<String, DuckingRequest> desired = new HashMap<>();
        for (ManagedSound sound : activeSounds.values()) {
            if (!sound.isStarted()) {
                continue;
            }

            ManagedSoundOptions.Ducking duck = sound.getOptions().ducking();
            if (duck == null) {
                continue;
            }

            desired.merge(
                    duck.group(),
                    new DuckingRequest(duck.amount(), duck.attackMs(), duck.releaseMs()),
                    DuckingRequest::merge
            );
        }

        Set<String> allGroups = new HashSet<>(duckingTargets.keySet());
        allGroups.addAll(desired.keySet());

        for (String group : allGroups) {
            DuckingRequest request = desired.get(group);
            float desiredAmount = request != null ? request.amount() : 0.0f;

            DuckState state = duckingTargets.computeIfAbsent(group, key -> new DuckState());
            if (request != null) {
                state.attackMs = request.attackMs();
                state.releaseMs = request.releaseMs();
            }

            long durationMs = desiredAmount > state.amount ? state.attackMs : state.releaseMs;
            state.amount = approach(state.amount, desiredAmount, dtMs, durationMs);

            if (desiredAmount <= 0.0001f && state.amount <= 0.0001f) {
                duckingTargets.remove(group);
            }
        }
    }

    private float getMixVolumeMultiplier(ManagedSound sound) {
        String group = sound.getOptions().mixGroup();
        if (group == null || group.isEmpty()) {
            return 1.0f;
        }
        DuckState state = duckingTargets.get(group);
        if (state == null) {
            return 1.0f;
        }
        return clamp(1.0f - state.amount, 0.0f, 1.0f);
    }

    private static float approach(float current, float target, long dtMs, long durationMs) {
        if (durationMs <= 0L || dtMs <= 0L) {
            return target;
        }
        float t = Math.min(1.0f, dtMs / (float) durationMs);
        return current + (target - current) * t;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class DuckState {
        private float amount;
        private long attackMs = 50L;
        private long releaseMs = 250L;
    }

    private record DuckingRequest(float amount, long attackMs, long releaseMs) {
        private static DuckingRequest merge(DuckingRequest a, DuckingRequest b) {
            float amount = Math.max(a.amount, b.amount);
            long attack = Math.min(a.attackMs, b.attackMs);
            long release = Math.max(a.releaseMs, b.releaseMs);
            return new DuckingRequest(amount, attack, release);
        }
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
