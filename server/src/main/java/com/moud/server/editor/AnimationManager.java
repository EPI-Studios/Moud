package com.moud.server.editor;

import com.moud.api.animation.AnimationClip;
import com.moud.api.animation.AnimationGson;
import com.moud.network.MoudPackets;
import com.moud.server.logging.LogContext;
import com.moud.server.logging.MoudLogger;
import com.moud.server.network.ServerNetworkManager;
import com.moud.plugin.animation.AnimationController;
import com.moud.server.editor.SceneDefaults;
import net.minestom.server.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class AnimationManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            AnimationManager.class,
            LogContext.builder().put("subsystem", "animation").build()
    );

    private static final AnimationManager INSTANCE = new AnimationManager();

    private Path animationsRoot;
    private final ConcurrentHashMap<String, PlaybackState> playing = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AnimationClip> loadedClips = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ControllerImpl> controllers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Runnable>> eventListeners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Runnable>> completionListeners = new ConcurrentHashMap<>();

    private AnimationManager() {
    }

    public static AnimationManager getInstance() {
        return INSTANCE;
    }

    public void initialize(Path projectRoot) {
        animationsRoot = projectRoot.resolve("animations");
        try {
            Files.createDirectories(animationsRoot);
        } catch (IOException e) {
            LOGGER.error("Failed to create animations directory {}", animationsRoot, e);
        }
    }

    public void handleSave(MoudPackets.AnimationSavePacket packet) {
        if (packet == null || packet.projectPath() == null) {
            return;
        }
        AnimationClip clip = packet.clip();
        if (clip == null) {
            LOGGER.warn("Rejected save of null clip for {}", packet.projectPath());
            return;
        }
        Path target = resolvePath(packet.projectPath());
        if (target == null) {
            LOGGER.warn("Invalid animation path {}", packet.projectPath());
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            String json = AnimationGson.instance().toJson(clip);
            Files.writeString(target, json, StandardCharsets.UTF_8);
            loadedClips.put(packet.projectPath(), clip);
            LOGGER.info("Saved animation '{}' to {}", clip.name(), target);
        } catch (IOException e) {
            LOGGER.error("Failed to save animation {}", target, e);
        }
    }

    public void handleLoad(MoudPackets.AnimationLoadPacket packet, ServerNetworkManager networkManager, Object player) {
        if (packet == null || packet.projectPath() == null) {
            return;
        }
        Path path = resolvePath(packet.projectPath());
        if (path == null) {
            sendLoadResponse(networkManager, player, packet.projectPath(), null, false, "Invalid path");
            return;
        }
        try {
            if (!Files.exists(path)) {
                sendLoadResponse(networkManager, player, packet.projectPath(), null, false, "File not found");
                return;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            AnimationClip clip = AnimationGson.instance().fromJson(json, AnimationClip.class);
            loadedClips.put(packet.projectPath(), clip);
            sendLoadResponse(networkManager, player, packet.projectPath(), clip, true, null);
        } catch (Exception e) {
            LOGGER.error("Failed to load animation {}", path, e);
            sendLoadResponse(networkManager, player, packet.projectPath(), null, false, e.getMessage());
        }
    }

    public void handleList(ServerNetworkManager networkManager, Object player) {
        List<MoudPackets.AnimationFileInfo> infos = new ArrayList<>();
        if (animationsRoot != null && Files.exists(animationsRoot)) {
            try {
                Files.walk(animationsRoot)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".an"))
                        .forEach(path -> {
                            String rel = animationsRoot.relativize(path).toString().replace('\\', '/');
                            float duration = 0f;
                            int trackCount = 0;
                            try {
                                String json = Files.readString(path, StandardCharsets.UTF_8);
                                AnimationClip clip = AnimationGson.instance().fromJson(json, AnimationClip.class);
                                if (clip != null) {
                                    duration = clip.duration();
                                    trackCount = clip.objectTracks() != null ? clip.objectTracks().size() : 0;
                                }
                            } catch (Exception ignored) {
                            }
                            infos.add(new MoudPackets.AnimationFileInfo(rel, path.getFileName().toString(), duration, trackCount));
                        });
            } catch (IOException e) {
                LOGGER.warn("Failed to list animations", e);
            }
        }
        networkManager.send((Player) player, new MoudPackets.AnimationListResponsePacket(infos));
    }

    public void handlePlay(MoudPackets.AnimationPlayPacket packet) {
        if (packet == null) {
            return;
        }
        AnimationClip clip = resolveClip(packet.animationId());
        if (clip == null) {
            LOGGER.warn("Cannot play animation {}, clip not found", packet.animationId());
            return;
        }
        playing.compute(packet.animationId(), (id, state) -> {
            float speed = packet.speed();
            if (speed <= 0f) speed = 1f;
            return new PlaybackState(clip, 0f, packet.loop(), speed, 0f);
        });
        LOGGER.info("Play animation {} (loop={}, speed={})", packet.animationId(), packet.loop(), packet.speed());
    }

    public void handleStop(MoudPackets.AnimationStopPacket packet) {
        if (packet == null) return;
        playing.remove(packet.animationId());
        LOGGER.info("Stop animation {}", packet.animationId());
    }

    public AnimationController controllerFor(String animationId) {
        if (animationId == null) return null;
        return controllers.computeIfAbsent(animationId, ControllerImpl::new);
    }

    public void handleSeek(MoudPackets.AnimationSeekPacket packet) {
        if (packet == null) return;
        AnimationClip clip = resolveClip(packet.animationId());
        if (clip == null) {
            return;
        }
        // Apply immediately so scrubbing updates the scene even if not "playing"
        applyClipAtTime(clip, packet.time(), packet.time());
        playing.computeIfPresent(packet.animationId(), (id, state) -> new PlaybackState(clip, packet.time(), state.loop(), state.speed(), state.time()));
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Seek animation {} to {}s", packet.animationId(), packet.time());
        }
    }

    private void sendLoadResponse(ServerNetworkManager networkManager, Object player, String path, AnimationClip clip, boolean success, String error) {
        if (networkManager == null) return;
        networkManager.send((Player) player, new MoudPackets.AnimationLoadResponsePacket(path, clip, success, error));
    }

    public void tick(double deltaSeconds) {
        if (playing.isEmpty()) {
            return;
        }
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, PlaybackState> entry : playing.entrySet()) {
            String id = entry.getKey();
            PlaybackState state = entry.getValue();
            AnimationClip clip = state.clip();
            float duration = clip.duration();
            float newTime = state.time() + (float) (deltaSeconds * state.speed());
            boolean looped = false;
            if (newTime > duration) {
                if (state.loop()) {
                    newTime = newTime % duration;
                    looped = true;
                } else {
                    toRemove.add(id);
                    fireCompletion(id);
                    continue;
                }
            }
            applyClipAtTime(clip, state.time(), newTime);
            playing.put(id, new PlaybackState(clip, newTime, state.loop(), state.speed(), state.time()));
            if (looped) {
                // TODO: implement looping
            }
        }
        toRemove.forEach(playing::remove);
    }

    private void applyClipAtTime(AnimationClip clip, float previousTime, float time) {
        if (clip == null || clip.objectTracks() == null) {
            return;
        }
        for (com.moud.api.animation.ObjectTrack objTrack : clip.objectTracks()) {
            SceneManager.SceneObject sceneObject = SceneManager.getInstance().getSceneObject(SceneDefaults.DEFAULT_SCENE_ID, objTrack.targetObjectId());
            if (sceneObject == null || objTrack.propertyTracks() == null) {
                continue;
            }
            for (Map.Entry<String, com.moud.api.animation.PropertyTrack> entry : objTrack.propertyTracks().entrySet()) {
                com.moud.api.animation.PropertyTrack track = entry.getValue();
                float value = sample(track, time);
                SceneManager.getInstance().applyAnimationProperty(SceneDefaults.DEFAULT_SCENE_ID, objTrack.targetObjectId(), entry.getKey(), track.propertyType(), value);
            }
        }

        if (clip.eventTrack() != null) {
            String targetId = (clip.objectTracks() != null && !clip.objectTracks().isEmpty())
                    ? clip.objectTracks().get(0).targetObjectId()
                    : "";
            for (com.moud.api.animation.EventKeyframe event : clip.eventTrack()) {
                if (event.time() >= previousTime && event.time() <= time) {
                    LOGGER.info("Animation event '{}' at {}s payload={}", event.name(), event.time(), event.payload());
                    ServerNetworkManager net = ServerNetworkManager.getInstance();
                    if (net != null) {
                        java.util.Map<String, String> payload = new java.util.HashMap<>();
                        if (event.payload() != null) {
                            payload.put("payload", event.payload());
                        }
                        net.broadcast(new MoudPackets.AnimationEventPacket(
                                clip.id(),
                                targetId,
                                event.name(),
                                payload
                        ));
                    }
                    fireEvent(clip.id(), event.name());
                }
            }
        }
    }

    private float sample(com.moud.api.animation.PropertyTrack track, float time) {
        List<com.moud.api.animation.Keyframe> keyframes = track.keyframes();
        if (keyframes == null || keyframes.isEmpty()) {
            return track.minValue();
        }
        com.moud.api.animation.Keyframe prev = null;
        com.moud.api.animation.Keyframe next = null;
        for (com.moud.api.animation.Keyframe kf : keyframes) {
            if (kf.time() <= time) {
                prev = kf;
            }
            if (kf.time() >= time) {
                next = kf;
                break;
            }
        }
        if (prev == null) {
            return next.value();
        }
        if (next == null) {
            return prev.value();
        }
        if (Math.abs(next.time() - prev.time()) < 1e-6) {
            return next.value();
        }
        float t = (time - prev.time()) / (next.time() - prev.time());
        return interpolate(prev, next, t);
    }

    private float interpolate(com.moud.api.animation.Keyframe a, com.moud.api.animation.Keyframe b, float t) {
        return switch (a.interpolation()) {
            case STEP -> a.value();
            case LINEAR -> a.value() + (b.value() - a.value()) * t;
            case SMOOTH -> smoothstep(a.value(), b.value(), t);
            case EASE_IN -> easeIn(a.value(), b.value(), t);
            case EASE_OUT -> easeOut(a.value(), b.value(), t);
            case BEZIER -> bezier(a.value(), b.value(), a.outTangent(), b.inTangent(), t);
        };
    }

    private float smoothstep(float a, float b, float t) {
        t = t * t * (3 - 2 * t);
        return a + (b - a) * t;
    }

    private float easeIn(float a, float b, float t) {
        t = t * t;
        return a + (b - a) * t;
    }

    private float easeOut(float a, float b, float t) {
        t = 1 - (1 - t) * (1 - t);
        return a + (b - a) * t;
    }

    private float bezier(float a, float b, float outTan, float inTan, float t) {
        float c1 = a + outTan;
        float c2 = b + inTan;
        float u = 1 - t;
        return (u * u * u) * a + 3 * (u * u) * t * c1 + 3 * u * (t * t) * c2 + (t * t * t) * b;
    }

    private AnimationClip resolveClip(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        AnimationClip cached = loadedClips.get(path);
        if (cached != null) {
            return cached;
        }
        Path file = resolvePath(path);
        if (file == null || !Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            AnimationClip clip = AnimationGson.instance().fromJson(json, AnimationClip.class);
            loadedClips.put(path, clip);
            return clip;
        } catch (IOException e) {
            LOGGER.warn("Failed to load clip {}: {}", path, e.getMessage());
            return null;
        }
    }

    private Path resolvePath(String projectPath) {
        if (animationsRoot == null) {
            return null;
        }
        String normalized = projectPath.replace('\\', '/');
        if (!normalized.endsWith(".an")) {
            normalized = normalized + ".an";
        }
        Path resolved = animationsRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(animationsRoot)) {
            return null;
        }
        return resolved;
    }

    private record PlaybackState(AnimationClip clip, float time, boolean loop, float speed, float lastTime) {}

    private final class ControllerImpl implements AnimationController {
        private final String id;

        private ControllerImpl(String id) {
            this.id = id;
        }

        @Override
        public void play() {
            handlePlay(new MoudPackets.AnimationPlayPacket(id, true, 1f));
        }

        @Override
        public void pause() {
            playing.remove(id);
        }

        @Override
        public void stop() {
            handleStop(new MoudPackets.AnimationStopPacket(id));
        }

        @Override
        public void seek(float timeSeconds) {
            handleSeek(new MoudPackets.AnimationSeekPacket(id, timeSeconds));
        }

        @Override
        public void setSpeed(float speed) {
            playing.computeIfPresent(id, (k, st) -> new PlaybackState(st.clip(), st.time(), st.loop(), speed, st.time()));
        }

        @Override
        public void setLoop(boolean loop) {
            playing.computeIfPresent(id, (k, st) -> new PlaybackState(st.clip(), st.time(), loop, st.speed(), st.time()));
        }

        @Override
        public float getTime() {
            PlaybackState st = playing.get(id);
            return st == null ? 0f : st.time();
        }

        @Override
        public float getDuration() {
            AnimationClip clip = resolveClip(id);
            return clip == null ? 0f : clip.duration();
        }

        @Override
        public boolean isPlaying() {
            return playing.containsKey(id);
        }

        @Override
        public void onEvent(String eventName, Runnable callback) {
            if (eventName == null || callback == null) return;
            eventListeners.computeIfAbsent(id + "|" + eventName, k -> new ArrayList<>()).add(callback);
        }

        @Override
        public void onComplete(Runnable callback) {
            if (callback == null) return;
            completionListeners.computeIfAbsent(id, k -> new ArrayList<>()).add(callback);
        }
    }

    private void fireEvent(String animationId, String eventName) {
        List<Runnable> list = eventListeners.get(animationId + "|" + eventName);
        if (list != null) {
            list.forEach(Runnable::run);
        }
    }

    private void fireCompletion(String animationId) {
        List<Runnable> list = completionListeners.get(animationId);
        if (list != null) {
            list.forEach(Runnable::run);
        }
    }
}
