package com.moud.server.audio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.MoudEngine;
import com.moud.server.events.EventDispatcher;
import com.moud.server.network.ServerNetworkManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ServerVoiceChatManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerVoiceChatManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ServerVoiceChatManager instance;

    private static final long LEVEL_EVENT_INTERVAL_MS = Long.getLong("moud.voice.levelIntervalMs", 100L);
    private static final long SPEAKING_TIMEOUT_MS = Long.getLong("moud.voice.speakingTimeoutMs", 350L);
    private static final long SESSION_TIMEOUT_MS = Long.getLong("moud.voice.sessionTimeoutMs", 10_000L);
    private static final int MAX_PACKET_BYTES = Integer.getInteger("moud.voice.maxPacketBytes", 32 * 1024);
    private static final int MAX_ACTIVE_STREAMS_PER_RECEIVER =
            Integer.getInteger("moud.voice.maxStreamsPerReceiver", 8);

    private final ConcurrentMap<UUID, VoiceSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, VoiceRoutingConfig> routingConfigs = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ReceiverState> receiverStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, VoiceRecording> recordings = new ConcurrentHashMap<>();

    private volatile boolean initialized;
    private Task tickTask;

    public static synchronized void install(ServerVoiceChatManager voiceChatManager) {
        instance = Objects.requireNonNull(voiceChatManager, "voiceChatManager");
    }

    public ServerVoiceChatManager() {
    }

    public static ServerVoiceChatManager getInstance() {
        if (instance == null) {
            instance = new ServerVoiceChatManager();
        }
        return instance;
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        tickTask = MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(TaskSchedule.millis(MinecraftServer.TICK_MS))
                .schedule();
        initialized = true;
        LOGGER.info("Voice chat manager initialized");
    }

    public synchronized void shutdown() {
        if (!initialized) {
            return;
        }
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        sessions.clear();
        routingConfigs.clear();
        receiverStates.clear();
        recordings.clear();
        initialized = false;
        LOGGER.info("Voice chat manager shut down");
    }

    public void handleVoiceChunk(Player speaker, MoudPackets.VoiceMicrophoneChunkPacket packet) {
        if (speaker == null || packet == null) {
            return;
        }
        if (packet.data() == null || packet.data().length == 0) {
            return;
        }
        if (packet.data().length > MAX_PACKET_BYTES) {
            LOGGER.warn(
                    "Dropping voice packet from {}: {} bytes > {}",
                    speaker.getUsername(),
                    packet.data().length,
                    MAX_PACKET_BYTES
            );
            return;
        }

        long nowMs = System.currentTimeMillis();

        VoiceSession session = sessions.computeIfAbsent(speaker.getUuid(), uuid -> new VoiceSession());
        VoiceRoutingConfig routing = routingConfigs.getOrDefault(speaker.getUuid(), VoiceRoutingConfig.defaults());

        boolean speakingNow = packet.speaking();

        CodecParams codecParams = new CodecParams(
                packet.codec(),
                packet.sampleRate(),
                packet.channels(),
                packet.frameSizeMs()
        );
        session.codecParams = codecParams;
        session.sessionId = packet.sessionId();
        session.level = packet.level();
        session.lastPacketAtMs = nowMs;
        if (speakingNow) {
            session.lastSpokeAtMs = nowMs;
        }

        if (speakingNow && !session.speaking) {
            session.speaking = true;
            dispatchVoiceEvent("player.voice.start", speaker, voiceStatePayload(session, routing, packet));
        } else if (!speakingNow && session.speaking) {
            session.speaking = false;
            dispatchVoiceEvent("player.voice.stop", speaker, voiceStatePayload(session, routing, packet));
        }

        if (nowMs - session.lastLevelEventAtMs >= LEVEL_EVENT_INTERVAL_MS) {
            session.lastLevelEventAtMs = nowMs;
            dispatchVoiceEvent("player.voice.level", speaker, voiceStatePayload(session, routing, packet));
        }

        if (shouldDispatchPacketEvent()) {
            dispatchVoiceEvent("player.voice.packet", speaker, voicePacketPayload(packet, routing));
        }

        if (session.activeRecordingId != null) {
            VoiceRecording recording = recordings.get(session.activeRecordingId);
            if (recording != null) {
                recording.appendFrame(speaker.getUuid(), codecParams, packet);
            }
        }

        routeToTargets(speaker, packet, routing);
    }

    public Map<String, Object> snapshotState(Player player) {
        if (player == null) {
            return null;
        }
        VoiceSession session = sessions.get(player.getUuid());
        VoiceRoutingConfig routing = routingConfigs.getOrDefault(player.getUuid(), VoiceRoutingConfig.defaults());
        Map<String, Object> payload = new HashMap<>();
        payload.put("active", session != null && session.active());
        payload.put("speaking", session != null && session.speaking);
        payload.put("level", session != null ? session.level : 0.0f);
        payload.put("lastSpokeAt", session != null ? session.lastSpokeAtMs : 0L);
        payload.put("lastPacketAt", session != null ? session.lastPacketAtMs : 0L);
        payload.put("sessionId", session != null ? session.sessionId : null);
        payload.put("codecParams", session != null ? session.codecParams.toMap() : null);
        payload.put("routing", routing.toMap());
        payload.put("recordingId", session != null ? session.activeRecordingId : null);
        return payload;
    }

    public void setRouting(UUID playerId, Map<String, Object> options) {
        if (playerId == null) {
            return;
        }
        if (options == null || options.isEmpty()) {
            return;
        }
        routingConfigs.compute(playerId, (uuid, current) -> {
            VoiceRoutingConfig base = current != null ? current : VoiceRoutingConfig.defaults();
            return base.merge(options);
        });
    }

    public Map<String, Object> getRouting(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return routingConfigs.getOrDefault(playerId, VoiceRoutingConfig.defaults()).toMap();
    }

    public String startRecording(UUID playerId, @Nullable String recordingId) {
        return startRecording(playerId, recordingId, 60_000L);
    }

    public String startRecording(UUID playerId, @Nullable String recordingId, long maxDurationMs) {
        if (playerId == null) {
            return null;
        }
        String id = recordingId != null && !recordingId.isBlank() ? recordingId : UUID.randomUUID().toString();
        long clampedMaxDuration = Math.max(1_000L, Math.min(maxDurationMs, 5 * 60_000L));
        recordings.put(id, new VoiceRecording(id, clampedMaxDuration));
        sessions.computeIfAbsent(playerId, uuid -> new VoiceSession()).activeRecordingId = id;
        return id;
    }

    public void stopRecording(UUID playerId) {
        if (playerId == null) {
            return;
        }
        VoiceSession session = sessions.get(playerId);
        if (session != null) {
            session.activeRecordingId = null;
        }
    }

    public void deleteRecording(String recordingId) {
        if (recordingId == null || recordingId.isBlank()) {
            return;
        }
        recordings.remove(recordingId);
        sessions.values().forEach(session -> {
            if (recordingId.equals(session.activeRecordingId)) {
                session.activeRecordingId = null;
            }
        });
    }

    public void replayRecording(String recordingId, Map<String, Object> options) {
        if (recordingId == null || recordingId.isBlank()) {
            return;
        }
        VoiceRecording recording = recordings.get(recordingId);
        if (recording == null) {
            return;
        }

        List<VoiceFrame> frames = recording.snapshotFrames();
        if (frames.isEmpty()) {
            return;
        }

        ReplayOptions parsed = ReplayOptions.from(options);
        UUID sourceSpeakerId = frames.getFirst().speakerId;
        List<Player> targets = resolveTargets(parsed, sourceSpeakerId);
        if (targets.isEmpty()) {
            return;
        }

        String replayId = parsed.replayId != null && !parsed.replayId.isBlank()
                ? parsed.replayId
                : "replay:" + recordingId;

        long startedAtMs = System.currentTimeMillis();
        long baseTimestamp = frames.getFirst().timestampMs;

        ReplayRunnable replayRunnable = new ReplayRunnable(
                frames,
                targets,
                parsed,
                replayId,
                startedAtMs,
                baseTimestamp
        );
        Task task = MinecraftServer.getSchedulerManager()
                .buildTask(replayRunnable)
                .repeat(TaskSchedule.millis(MinecraftServer.TICK_MS))
                .schedule();
        replayRunnable.setTask(task);
    }

    private List<Player> resolveTargets(ReplayOptions options, @Nullable UUID originPlayerId) {
        if (options == null) {
            return List.of();
        }

        Collection<Player> online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (online.isEmpty()) {
            return List.of();
        }

        if (options.targetUuids != null && !options.targetUuids.isEmpty()) {
            List<Player> targets = new ArrayList<>();
            for (Player player : online) {
                if (options.targetUuids.contains(player.getUuid())) {
                    targets.add(player);
                }
            }
            return targets;
        }

        if (options.range != null) {
            Vector3 originPosition = options.position;
            if (originPosition == null && originPlayerId != null) {
                Player originPlayer = findOnlinePlayer(originPlayerId);
                if (originPlayer != null) {
                    originPosition = new Vector3(
                            originPlayer.getPosition().x(),
                            originPlayer.getPosition().y(),
                            originPlayer.getPosition().z()
                    );
                }
            }

            if (originPosition == null) {
                return List.of();
            }

            double rangeSq = options.range * options.range;
            Pos origin = new Pos(originPosition.x, originPosition.y, originPosition.z);
            List<Player> targets = new ArrayList<>();
            for (Player player : online) {
                if (player.getPosition().distanceSquared(origin) <= rangeSq) {
                    targets.add(player);
                }
            }
            return targets;
        }

        return new ArrayList<>(online);
    }

    private void routeToTargets(Player speaker,
                                MoudPackets.VoiceMicrophoneChunkPacket packet,
                                VoiceRoutingConfig routing) {
        ServerNetworkManager network = ServerNetworkManager.getInstance();
        if (network == null) {
            return;
        }

        Collection<Player> online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (online.isEmpty()) {
            return;
        }

        Vector3 position = routing.positional
                ? new Vector3(speaker.getPosition().x(), speaker.getPosition().y(), speaker.getPosition().z())
                : null;

        for (Player receiver : online) {
            if (receiver.getUuid().equals(speaker.getUuid())) {
                continue;
            }
            if (!shouldReceive(routing, speaker, receiver)) {
                continue;
            }

            ReceiverState receiverState = receiverStates.computeIfAbsent(
                    receiver.getUuid(),
                    uuid -> new ReceiverState()
            );
            if (!receiverState.allowStream(speaker.getUuid(), routing.priority, packet.speaking())) {
                continue;
            }

            MoudPackets.VoiceStreamChunkPacket out = new MoudPackets.VoiceStreamChunkPacket(
                    speaker.getUuid(),
                    packet.sessionId(),
                    packet.sequence(),
                    packet.timestampMs(),
                    packet.codec(),
                    packet.sampleRate(),
                    packet.channels(),
                    packet.frameSizeMs(),
                    packet.level(),
                    packet.speaking(),
                    packet.data(),
                    routing.outputProcessing,
                    position,
                    null
            );
            network.send(receiver, out);
        }
    }

    private boolean shouldReceive(VoiceRoutingConfig routing, Player speaker, Player receiver) {
        if (routing == null) {
            return false;
        }

        return switch (routing.mode) {
            case PROXIMITY -> {
                double range = routing.effectiveRange();
                yield receiver.getPosition().distanceSquared(speaker.getPosition()) <= range * range;
            }
            case CHANNEL, RADIO -> {
                if (routing.channel == null || routing.channel.isBlank()) {
                    yield false;
                }
                VoiceRoutingConfig receiverConfig = routingConfigs.getOrDefault(
                        receiver.getUuid(),
                        VoiceRoutingConfig.defaults()
                );
                yield Objects.equals(routing.channel, receiverConfig.channel);
            }
            case DIRECT -> routing.targets.contains(receiver.getUuid());
        };
    }

    private void tick() {
        long nowMs = System.currentTimeMillis();

        sessions.forEach((uuid, session) -> {
            long sincePacket = nowMs - session.lastPacketAtMs;
            if (session.speaking && sincePacket > SPEAKING_TIMEOUT_MS) {
                session.speaking = false;
                Player player = findOnlinePlayer(uuid);
                if (player != null) {
                    VoiceRoutingConfig routing = routingConfigs.getOrDefault(uuid, VoiceRoutingConfig.defaults());
                    dispatchVoiceEvent("player.voice.stop", player, voiceStatePayload(session, routing, null));
                }
            }
            if (sincePacket > SESSION_TIMEOUT_MS) {
                sessions.remove(uuid, session);
            }
        });

        receiverStates.forEach((receiverId, state) -> {
            state.cleanup(nowMs);
            if (state.isEmpty()) {
                receiverStates.remove(receiverId, state);
            }
        });
    }

    @Nullable
    private static Player findOnlinePlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getUuid().equals(playerId)) {
                return player;
            }
        }
        return null;
    }

    private final class ReplayRunnable implements Runnable {
        private final List<VoiceFrame> frames;
        private final List<Player> targets;
        private final ReplayOptions options;
        private final String replayId;
        private final long startedAtMs;
        private final long baseTimestampMs;
        private Task task;
        private int index;

        private ReplayRunnable(List<VoiceFrame> frames,
                               List<Player> targets,
                               ReplayOptions options,
                               String replayId,
                               long startedAtMs,
                               long baseTimestampMs) {
            this.frames = frames;
            this.targets = targets;
            this.options = options;
            this.replayId = replayId;
            this.startedAtMs = startedAtMs;
            this.baseTimestampMs = baseTimestampMs;
        }

        private void setTask(Task task) {
            this.task = task;
        }

        @Override
        public void run() {
            long nowMs = System.currentTimeMillis();
            long elapsedMs = Math.max(0L, nowMs - startedAtMs);

            while (index < frames.size()) {
                VoiceFrame frame = frames.get(index);
                long dueMs = Math.max(0L, frame.timestampMs - baseTimestampMs);
                if (dueMs > elapsedMs + 25L) {
                    break;
                }

                MoudPackets.VoiceStreamChunkPacket out = new MoudPackets.VoiceStreamChunkPacket(
                        frame.speakerId,
                        frame.sessionId,
                        frame.sequence,
                        frame.timestampMs,
                        frame.codecParams.codec(),
                        frame.codecParams.sampleRate(),
                        frame.codecParams.channels(),
                        frame.codecParams.frameSizeMs(),
                        frame.level,
                        frame.speaking,
                        frame.data,
                        options.outputProcessing,
                        options.position,
                        replayId
                );

                ServerNetworkManager network = ServerNetworkManager.getInstance();
                if (network != null) {
                    network.sendToPlayers(out, targets);
                }
                index++;
            }

            if (index >= frames.size() && task != null) {
                task.cancel();
            }
        }
    }

    private boolean shouldDispatchPacketEvent() {
        MoudEngine engine = MoudEngine.getInstance();
        if (engine == null) {
            return false;
        }
        EventDispatcher dispatcher = engine.getEventDispatcher();
        return dispatcher != null && dispatcher.hasHandlers("player.voice.packet");
    }

    private void dispatchVoiceEvent(String eventName, Player player, Map<String, Object> payload) {
        MoudEngine engine = MoudEngine.getInstance();
        if (engine == null || player == null) {
            return;
        }
        EventDispatcher dispatcher = engine.getEventDispatcher();
        if (dispatcher == null) {
            return;
        }
        if (!dispatcher.hasHandlers(eventName)) {
            return;
        }

        try {
            String json = payload == null || payload.isEmpty() ? "" : MAPPER.writeValueAsString(payload);
            dispatcher.dispatchScriptEvent(eventName, json, player);
            com.moud.server.plugin.PluginEventBus.getInstance().dispatchScriptEvent(eventName, player, json);
        } catch (JsonProcessingException e) {
            LOGGER.warn("Failed to serialize voice event payload for {}", eventName, e);
        }
    }

    private static Map<String, Object> voicePacketPayload(MoudPackets.VoiceMicrophoneChunkPacket packet,
                                                          VoiceRoutingConfig routing) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", packet.sessionId());
        payload.put("sequence", packet.sequence());
        payload.put("timestampMs", packet.timestampMs());
        payload.put("codec", packet.codec());
        payload.put("sampleRate", packet.sampleRate());
        payload.put("channels", packet.channels());
        payload.put("frameSizeMs", packet.frameSizeMs());
        payload.put("level", packet.level());
        payload.put("speaking", packet.speaking());
        payload.put("byteLength", packet.data() != null ? packet.data().length : 0);
        payload.put("routing", routing.toMap());
        return payload;
    }

    private static Map<String, Object> voiceStatePayload(VoiceSession session,
                                                        VoiceRoutingConfig routing,
                                                        @Nullable MoudPackets.VoiceMicrophoneChunkPacket packet) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", session.sessionId);
        payload.put("speaking", session.speaking);
        payload.put("level", session.level);
        payload.put("lastSpokeAt", session.lastSpokeAtMs);
        payload.put("lastPacketAt", session.lastPacketAtMs);
        payload.put("codecParams", session.codecParams.toMap());
        payload.put("routing", routing.toMap());
        if (packet != null) {
            payload.put("sequence", packet.sequence());
            payload.put("timestampMs", packet.timestampMs());
        }
        return payload;
    }

    private static final class VoiceSession {
        private String sessionId = "";
        private boolean speaking;
        private float level;
        private long lastPacketAtMs;
        private long lastSpokeAtMs;
        private long lastLevelEventAtMs;
        private CodecParams codecParams = CodecParams.defaults();
        private String activeRecordingId;

        private boolean active() {
            return (System.currentTimeMillis() - lastPacketAtMs) <= SESSION_TIMEOUT_MS;
        }
    }

    private record CodecParams(String codec, int sampleRate, int channels, int frameSizeMs) {
        static CodecParams defaults() {
            return new CodecParams("pcm_s16le", 48000, 1, 20);
        }

        Map<String, Object> toMap() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("codec", codec);
            payload.put("sampleRate", sampleRate);
            payload.put("channels", channels);
            payload.put("frameSizeMs", frameSizeMs);
            return payload;
        }
    }

    private enum SpeechMode {
        WHISPER,
        NORMAL,
        SHOUT
    }

    private enum RouteMode {
        PROXIMITY,
        CHANNEL,
        RADIO,
        DIRECT
    }

    private static final class VoiceRoutingConfig {
        private final RouteMode mode;
        private final double range;
        private final SpeechMode speechMode;
        private final String channel;
        private final List<UUID> targets;
        private final int priority;
        private final boolean positional;
        private final Map<String, Object> outputProcessing;

        private VoiceRoutingConfig(RouteMode mode,
                                  double range,
                                  SpeechMode speechMode,
                                  @Nullable String channel,
                                  @Nullable List<UUID> targets,
                                  int priority,
                                  boolean positional,
                                  @Nullable Map<String, Object> outputProcessing) {
            this.mode = mode;
            this.range = range;
            this.speechMode = speechMode;
            this.channel = channel == null ? "" : channel;
            this.targets = targets != null ? List.copyOf(targets) : List.of();
            this.priority = priority;
            this.positional = positional;
            this.outputProcessing = outputProcessing != null && !outputProcessing.isEmpty()
                    ? new HashMap<>(outputProcessing)
                    : null;
        }

        static VoiceRoutingConfig defaults() {
            return new VoiceRoutingConfig(RouteMode.PROXIMITY, 16.0, SpeechMode.NORMAL, null, null, 0, true, null);
        }

        double effectiveRange() {
            double base = Math.max(0.0, range);
            return switch (speechMode) {
                case WHISPER -> base * 0.6;
                case SHOUT -> base * 1.6;
                default -> base;
            };
        }

        Map<String, Object> toMap() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("mode", mode.name().toLowerCase());
            payload.put("range", range);
            payload.put("speechMode", speechMode.name().toLowerCase());
            payload.put("channel", channel.isEmpty() ? null : channel);
            if (!targets.isEmpty()) {
                List<String> ids = new ArrayList<>(targets.size());
                for (UUID uuid : targets) {
                    ids.add(uuid.toString());
                }
                payload.put("targets", ids);
            }
            payload.put("priority", priority);
            payload.put("positional", positional);
            payload.put("outputProcessing", outputProcessing);
            return payload;
        }

        static VoiceRoutingConfig from(@Nullable Map<String, Object> options) {
            if (options == null || options.isEmpty()) {
                return defaults();
            }

            RouteMode mode = parseMode(options.get("mode"));
            double range = parseDouble(options.get("range"), 16.0);
            SpeechMode speech = parseSpeechMode(options.get("speechMode"));
            String channel = options.get("channel") instanceof String raw ? raw : "";
            List<UUID> targets = parseTargets(options.get("targets"));
            int priority = (int) Math.max(
                    Integer.MIN_VALUE,
                    Math.min(Integer.MAX_VALUE, parseDouble(options.get("priority"), 0.0))
            );
            boolean positional = parseBoolean(options.get("positional"), true);
            Map<String, Object> outputProcessing = options.get("outputProcessing") instanceof Map<?, ?> map
                    ? toStringObjectMap(map)
                    : null;
            return new VoiceRoutingConfig(
                    mode,
                    range,
                    speech,
                    channel,
                    targets,
                    priority,
                    positional,
                    outputProcessing
            );
        }

        VoiceRoutingConfig merge(@Nullable Map<String, Object> options) {
            if (options == null || options.isEmpty()) {
                return this;
            }

            RouteMode nextMode = options.containsKey("mode") ? parseMode(options.get("mode")) : this.mode;
            double nextRange = options.containsKey("range") ? parseDouble(options.get("range"), this.range) : this.range;
            SpeechMode nextSpeech = options.containsKey("speechMode")
                    ? parseSpeechMode(options.get("speechMode"))
                    : this.speechMode;
            String nextChannel = this.channel;
            if (options.containsKey("channel")) {
                Object raw = options.get("channel");
                nextChannel = raw instanceof String string ? string : "";
            }
            List<UUID> nextTargets = options.containsKey("targets") ? parseTargets(options.get("targets")) : this.targets;

            int nextPriority = this.priority;
            if (options.containsKey("priority")) {
                nextPriority = (int) Math.max(
                        Integer.MIN_VALUE,
                        Math.min(Integer.MAX_VALUE, parseDouble(options.get("priority"), 0.0))
                );
            }

            boolean nextPositional = options.containsKey("positional")
                    ? parseBoolean(options.get("positional"), this.positional)
                    : this.positional;

            Map<String, Object> nextOutputProcessing = this.outputProcessing;
            if (options.containsKey("outputProcessing")) {
                Object raw = options.get("outputProcessing");
                if (raw instanceof Map<?, ?> map) {
                    nextOutputProcessing = toStringObjectMap(map);
                } else {
                    nextOutputProcessing = null;
                }
            }

            return new VoiceRoutingConfig(
                    nextMode,
                    nextRange,
                    nextSpeech,
                    nextChannel,
                    nextTargets,
                    nextPriority,
                    nextPositional,
                    nextOutputProcessing
            );
        }

        private static RouteMode parseMode(Object value) {
            if (value instanceof String raw) {
                String normalized = raw.trim().toLowerCase();
                return switch (normalized) {
                    case "channel" -> RouteMode.CHANNEL;
                    case "radio" -> RouteMode.RADIO;
                    case "direct" -> RouteMode.DIRECT;
                    default -> RouteMode.PROXIMITY;
                };
            }
            return RouteMode.PROXIMITY;
        }

        private static SpeechMode parseSpeechMode(Object value) {
            if (value instanceof String raw) {
                String normalized = raw.trim().toLowerCase();
                return switch (normalized) {
                    case "whisper" -> SpeechMode.WHISPER;
                    case "shout" -> SpeechMode.SHOUT;
                    default -> SpeechMode.NORMAL;
                };
            }
            return SpeechMode.NORMAL;
        }

        private static boolean parseBoolean(Object value, boolean fallback) {
            return value instanceof Boolean bool ? bool : fallback;
        }

        private static double parseDouble(Object value, double fallback) {
            return value instanceof Number number ? number.doubleValue() : fallback;
        }

        private static List<UUID> parseTargets(Object value) {
            if (value instanceof List<?> list) {
                List<UUID> uuids = new ArrayList<>();
                for (Object element : list) {
                    UUID uuid = parseUuid(element);
                    if (uuid != null) {
                        uuids.add(uuid);
                    }
                }
                return uuids;
            }
            if (value instanceof Object[] array) {
                List<UUID> uuids = new ArrayList<>();
                for (Object element : array) {
                    UUID uuid = parseUuid(element);
                    if (uuid != null) {
                        uuids.add(uuid);
                    }
                }
                return uuids;
            }
            return List.of();
        }

        private static UUID parseUuid(Object value) {
            if (value instanceof UUID uuid) {
                return uuid;
            }
            if (value instanceof String raw && !raw.isBlank()) {
                try {
                    return UUID.fromString(raw);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
            Map<String, Object> map = new HashMap<>();
            raw.forEach((key, value) -> map.put(String.valueOf(key), value));
            return map;
        }
    }

    private static final class ReceiverState {
        private final ConcurrentMap<UUID, ActiveSpeaker> activeSpeakers = new ConcurrentHashMap<>();

        boolean allowStream(UUID speakerId, int priority, boolean speaking) {
            long nowMs = System.currentTimeMillis();
            ActiveSpeaker existing = activeSpeakers.get(speakerId);
            if (existing != null) {
                existing.lastSentAtMs = nowMs;
                existing.priority = priority;
                return true;
            }

            if (!speaking) {
                return activeSpeakers.size() < MAX_ACTIVE_STREAMS_PER_RECEIVER;
            }

            if (activeSpeakers.size() < MAX_ACTIVE_STREAMS_PER_RECEIVER) {
                activeSpeakers.put(speakerId, new ActiveSpeaker(priority, nowMs));
                return true;
            }

            UUID toEvict = activeSpeakers.entrySet().stream()
                    .min(Comparator.comparingInt(entry -> entry.getValue().priority))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (toEvict == null) {
                return false;
            }
            int lowest = activeSpeakers.get(toEvict).priority;
            if (priority <= lowest) {
                return false;
            }
            activeSpeakers.remove(toEvict);
            activeSpeakers.put(speakerId, new ActiveSpeaker(priority, nowMs));
            return true;
        }

        void cleanup(long nowMs) {
            activeSpeakers.entrySet().removeIf(entry -> nowMs - entry.getValue().lastSentAtMs > SESSION_TIMEOUT_MS);
        }

        boolean isEmpty() {
            return activeSpeakers.isEmpty();
        }

        private static final class ActiveSpeaker {
            private volatile int priority;
            private volatile long lastSentAtMs;

            private ActiveSpeaker(int priority, long lastSentAtMs) {
                this.priority = priority;
                this.lastSentAtMs = lastSentAtMs;
            }
        }
    }

    private static final class VoiceRecording {
        private final String id;
        private final long maxDurationMs;
        private final List<VoiceFrame> frames = new ArrayList<>();
        private volatile boolean closed;

        private VoiceRecording(String id, long maxDurationMs) {
            this.id = id;
            this.maxDurationMs = maxDurationMs;
        }

        synchronized void appendFrame(UUID speakerId,
                                      CodecParams codecParams,
                                      MoudPackets.VoiceMicrophoneChunkPacket packet) {
            if (closed) {
                return;
            }
            if (!frames.isEmpty()) {
                long baseTimestamp = frames.getFirst().timestampMs;
                long duration = packet.timestampMs() - baseTimestamp;
                if (duration > maxDurationMs) {
                    closed = true;
                    return;
                }
            }

            frames.add(new VoiceFrame(
                    speakerId,
                    packet.sessionId(),
                    packet.sequence(),
                    packet.timestampMs(),
                    codecParams,
                    packet.level(),
                    packet.speaking(),
                    packet.data() != null ? packet.data().clone() : new byte[0]
            ));
        }

        synchronized List<VoiceFrame> snapshotFrames() {
            return new ArrayList<>(frames);
        }
    }

    private static final class VoiceFrame {
        private final UUID speakerId;
        private final String sessionId;
        private final int sequence;
        private final long timestampMs;
        private final CodecParams codecParams;
        private final float level;
        private final boolean speaking;
        private final byte[] data;

        private VoiceFrame(UUID speakerId,
                           String sessionId,
                           int sequence,
                           long timestampMs,
                           CodecParams codecParams,
                           float level,
                           boolean speaking,
                           byte[] data) {
            this.speakerId = Objects.requireNonNull(speakerId, "speakerId");
            this.sessionId = sessionId == null ? "" : sessionId;
            this.sequence = sequence;
            this.timestampMs = timestampMs;
            this.codecParams = codecParams;
            this.level = level;
            this.speaking = speaking;
            this.data = data != null ? data : new byte[0];
        }
    }

    private static final class ReplayOptions {
        private final List<UUID> targetUuids;
        private final Double range;
        private final Vector3 position;
        private final Map<String, Object> outputProcessing;
        private final String replayId;

        private ReplayOptions(@Nullable List<UUID> targetUuids,
                              @Nullable Double range,
                              @Nullable Vector3 position,
                              @Nullable Map<String, Object> outputProcessing,
                              @Nullable String replayId) {
            this.targetUuids = targetUuids != null ? List.copyOf(targetUuids) : null;
            this.range = range;
            this.position = position;
            this.outputProcessing = outputProcessing != null && !outputProcessing.isEmpty()
                    ? new HashMap<>(outputProcessing)
                    : null;
            this.replayId = replayId;
        }

        static ReplayOptions from(@Nullable Map<String, Object> options) {
            if (options == null || options.isEmpty()) {
                return new ReplayOptions(null, null, null, null, null);
            }

            List<UUID> targets = null;
            Object rawTargets = options.get("targets");
            if (rawTargets instanceof List<?> list) {
                targets = new ArrayList<>();
                for (Object element : list) {
                    UUID uuid = VoiceRoutingConfig.parseUuid(element);
                    if (uuid != null) {
                        targets.add(uuid);
                    }
                }
            } else if (rawTargets instanceof Object[] array) {
                targets = new ArrayList<>();
                for (Object element : array) {
                    UUID uuid = VoiceRoutingConfig.parseUuid(element);
                    if (uuid != null) {
                        targets.add(uuid);
                    }
                }
            }

            Double range = options.get("range") instanceof Number number ? number.doubleValue() : null;

            Vector3 position = null;
            Object posValue = options.get("position");
            if (posValue instanceof Vector3 vector3) {
                position = vector3;
            } else if (posValue instanceof Map<?, ?> posMap) {
                Object x = posMap.get("x");
                Object y = posMap.get("y");
                Object z = posMap.get("z");
                if (x instanceof Number nx && y instanceof Number ny && z instanceof Number nz) {
                    position = new Vector3(nx.doubleValue(), ny.doubleValue(), nz.doubleValue());
                }
            } else if (posValue instanceof List<?> posList && posList.size() >= 3) {
                Object x = posList.get(0);
                Object y = posList.get(1);
                Object z = posList.get(2);
                if (x instanceof Number nx && y instanceof Number ny && z instanceof Number nz) {
                    position = new Vector3(nx.doubleValue(), ny.doubleValue(), nz.doubleValue());
                }
            } else if (posValue instanceof Object[] posArray && posArray.length >= 3) {
                Object x = posArray[0];
                Object y = posArray[1];
                Object z = posArray[2];
                if (x instanceof Number nx && y instanceof Number ny && z instanceof Number nz) {
                    position = new Vector3(nx.doubleValue(), ny.doubleValue(), nz.doubleValue());
                }
            }

            Map<String, Object> outputProcessing = options.get("outputProcessing") instanceof Map<?, ?> map
                    ? VoiceRoutingConfig.toStringObjectMap(map)
                    : null;

            String replayId = options.get("replayId") instanceof String raw && !raw.isBlank() ? raw : null;

            return new ReplayOptions(targets, range, position, outputProcessing, replayId);
        }
    }
}
