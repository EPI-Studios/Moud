package com.moud.server.editor;

import com.moud.api.animation.AnimationClip;
import com.moud.api.animation.AnimationGson;
import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
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
import java.util.Objects;


public final class AnimationManager {
    private static final MoudLogger LOGGER = MoudLogger.getLogger(
            AnimationManager.class,
            LogContext.builder().put("subsystem", "animation").build()
    );

    private static AnimationManager instance;

    private Path animationsRoot;
    private final ConcurrentHashMap<String, PlaybackState> playing = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AnimationClip> loadedClips = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ControllerImpl> controllers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Runnable>> eventListeners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Runnable>> completionListeners = new ConcurrentHashMap<>();

    public static synchronized void install(AnimationManager animationManager) {
        instance = Objects.requireNonNull(animationManager, "animationManager");
    }

    public AnimationManager(Path projectRoot) {
        initialize(projectRoot);
    }

    public AnimationManager() {
    }

    public static AnimationManager getInstance() {
        if (instance == null) {
            instance = new AnimationManager();
        }
        return instance;
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

    public void handleLoad(MoudPackets.AnimationLoadPacket packet, ServerNetworkManager networkManager, Player player) {
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

    public void handleList(ServerNetworkManager networkManager, Player player) {
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
        if (networkManager == null || player == null) {
            return;
        }
        networkManager.send(player, new MoudPackets.AnimationListResponsePacket(infos));
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

    private void sendLoadResponse(ServerNetworkManager networkManager, Player player, String path, AnimationClip clip, boolean success, String error) {
        if (networkManager == null || player == null) {
            return;
        }
        networkManager.send(player, new MoudPackets.AnimationLoadResponsePacket(path, clip, success, error));
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
        Map<String, TransformUpdate> pendingUpdates = new java.util.HashMap<>();

        for (com.moud.api.animation.ObjectTrack objTrack : clip.objectTracks()) {
            SceneManager.SceneObject sceneObject = SceneManager.getInstance().getSceneObject(SceneDefaults.DEFAULT_SCENE_ID, objTrack.targetObjectId());
            if (sceneObject == null || objTrack.propertyTracks() == null) {
                continue;
            }

            TransformUpdate update = pendingUpdates.computeIfAbsent(
                    objTrack.targetObjectId(),
                    id -> new TransformUpdate(sceneObject)
            );

            for (Map.Entry<String, com.moud.api.animation.PropertyTrack> entry : objTrack.propertyTracks().entrySet()) {
                com.moud.api.animation.PropertyTrack track = entry.getValue();
                float value = sample(track, time);
                update.applyProperty(entry.getKey(), value);
            }
        }

        pendingUpdates.forEach((objectId, update) ->
                SceneManager.getInstance().applyAnimationFrame(SceneDefaults.DEFAULT_SCENE_ID, objectId, update)
        );

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

    static final class TransformUpdate {
        private Vector3 position;
        private Vector3 rotationEuler;
        private Quaternion rotationQuat;
        private Vector3 scale;
        private final Map<String, Float> scalarProperties = new java.util.HashMap<>();
        private boolean positionChanged;
        private boolean rotationChanged;
        private boolean scaleChanged;

        TransformUpdate(SceneManager.SceneObject sceneObject) {
            Map<String, Object> props = sceneObject != null ? sceneObject.getProperties() : null;
            if (props != null) {
                this.position = vectorProperty(props.get("position"), null);
                this.rotationEuler = rotationProperty(props.get("rotation"), null);
                this.rotationQuat = quaternionProperty(props.get("rotationQuat"), null);
                this.scale = vectorProperty(props.get("scale"), null);
            }
        }

        void applyProperty(String key, float value) {
            if (key == null) {
                return;
            }
            switch (key) {
                case "position.x" -> {
                    ensurePosition();
                    position.x = value;
                    positionChanged = true;
                }
                case "position.y" -> {
                    ensurePosition();
                    position.y = value;
                    positionChanged = true;
                }
                case "position.z" -> {
                    ensurePosition();
                    position.z = value;
                    positionChanged = true;
                }
                case "rotation.x", "rotation.pitch" -> {
                    ensureRotation();
                    rotationEuler.x = value;
                    rotationChanged = true;
                    rotationQuat = null;
                }
                case "rotation.y", "rotation.yaw" -> {
                    ensureRotation();
                    rotationEuler.y = value;
                    rotationChanged = true;
                    rotationQuat = null;
                }
                case "rotation.z", "rotation.roll" -> {
                    ensureRotation();
                    rotationEuler.z = value;
                    rotationChanged = true;
                    rotationQuat = null;
                }
                case "rotationQuat.x" -> {
                    ensureRotationQuat();
                    rotationQuat.x = value;
                    rotationChanged = true;
                }
                case "rotationQuat.y" -> {
                    ensureRotationQuat();
                    rotationQuat.y = value;
                    rotationChanged = true;
                }
                case "rotationQuat.z" -> {
                    ensureRotationQuat();
                    rotationQuat.z = value;
                    rotationChanged = true;
                }
                case "rotationQuat.w" -> {
                    ensureRotationQuat();
                    rotationQuat.w = value;
                    rotationChanged = true;
                }
                case "scale.x" -> {
                    ensureScale();
                    scale.x = (float) Math.max(0.0001, value);
                    scaleChanged = true;
                }
                case "scale.y" -> {
                    ensureScale();
                    scale.y = (float) Math.max(0.0001, value);
                    scaleChanged = true;
                }
                case "scale.z" -> {
                    ensureScale();
                    scale.z = (float) Math.max(0.0001, value);
                    scaleChanged = true;
                }
                default -> scalarProperties.put(key, value);
            }
        }

        Vector3 positionIfChanged() {
            return positionChanged ? position : null;
        }

        Vector3 rotationEulerIfChanged() {
            if (!rotationChanged) {
                return null;
            }
            if (rotationEuler == null && rotationQuat != null) {
                rotationEuler = rotationQuat.toEuler();
            }
            return rotationEuler;
        }

        Quaternion rotationQuatIfChanged() {
            if (!rotationChanged) {
                return null;
            }
            if (rotationQuat != null) {
                return rotationQuat;
            }
            if (rotationEuler == null) {
                return null;
            }
            rotationQuat = Quaternion.fromEuler(
                    (float) rotationEuler.x,
                    (float) rotationEuler.y,
                    (float) rotationEuler.z
            );
            return rotationQuat;
        }

        Vector3 scaleIfChanged() {
            return scaleChanged ? scale : null;
        }

        Map<String, Float> scalarProperties() {
            return scalarProperties;
        }

        private void ensurePosition() {
            if (position == null) {
                position = new Vector3(0, 0, 0);
            }
        }

        private void ensureRotation() {
            if (rotationEuler == null) {
                rotationEuler = new Vector3(0, 0, 0);
            }
        }

        private void ensureRotationQuat() {
            if (rotationQuat == null) {
                rotationQuat = Quaternion.identity();
            }
        }

        private void ensureScale() {
            if (scale == null) {
                scale = Vector3.one();
            }
        }
    }

    private static Vector3 vectorProperty(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), fallback != null ? fallback.x : 0.0);
            double y = toDouble(map.get("y"), fallback != null ? fallback.y : 0.0);
            double z = toDouble(map.get("z"), fallback != null ? fallback.z : 0.0);
            return new Vector3(x, y, z);
        }
        return fallback;
    }

    private static Vector3 rotationProperty(Object raw, Vector3 fallback) {
        if (raw instanceof Map<?, ?> map) {
            boolean hasEuler = map.containsKey("pitch") || map.containsKey("yaw") || map.containsKey("roll");
            double x = toDouble(hasEuler ? map.get("pitch") : map.get("x"), fallback != null ? fallback.x : 0.0);
            double y = toDouble(hasEuler ? map.get("yaw") : map.get("y"), fallback != null ? fallback.y : 0.0);
            double z = toDouble(hasEuler ? map.get("roll") : map.get("z"), fallback != null ? fallback.z : 0.0);
            return new Vector3(x, y, z);
        }
        return fallback;
    }

    private static Quaternion quaternionProperty(Object raw, Quaternion fallback) {
        if (raw instanceof Map<?, ?> map) {
            double x = toDouble(map.get("x"), 0.0);
            double y = toDouble(map.get("y"), 0.0);
            double z = toDouble(map.get("z"), 0.0);
            double w = toDouble(map.get("w"), 1.0);
            return new Quaternion((float) x, (float) y, (float) z, (float) w);
        }
        return fallback;
    }

    private static double toDouble(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return raw != null ? Double.parseDouble(raw.toString()) : fallback;
        } catch (Exception e) {
            return fallback;
        }
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
