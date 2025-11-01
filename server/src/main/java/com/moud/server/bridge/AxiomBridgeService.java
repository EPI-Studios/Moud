package com.moud.server.bridge;

import com.moud.api.math.Vector3;
import com.moud.server.instance.InstanceManager;
import com.moud.server.lighting.ServerLightingManager;
import com.moud.server.logging.MoudLogger;
import com.moud.server.proxy.PlayerModelProxy;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AxiomBridgeService {

    private static final MoudLogger LOGGER = MoudLogger.getLogger(AxiomBridgeService.class);

    private static final String CHANNEL_HELLO = "axiom:hello";
    private static final String CHANNEL_ENABLE = "axiom:enable";
    private static final String CHANNEL_RESTRICTIONS = "axiom:restrictions";
    private static final String CHANNEL_MARKER_DATA = "axiom:marker_data";
    private static final String CHANNEL_MANIPULATE_ENTITY = "axiom:manipulate_entity";

    private static final List<String> SERVER_CHANNELS = List.of(
            CHANNEL_HELLO,
            CHANNEL_ENABLE,
            CHANNEL_RESTRICTIONS,
            CHANNEL_MARKER_DATA,
            CHANNEL_MANIPULATE_ENTITY
    );

    private static final Vec REGION_OFFSET_MIN = new Vec(-0.5, 0.0, -0.5);
    private static final Vec REGION_OFFSET_MAX = new Vec(0.5, 2.0, 0.5);
    private static final int REGION_LINE_COLOR = MarkerTags.argbToInt(255, 255, 215, 0);
    private static final int REGION_FACE_COLOR = MarkerTags.argbToInt(40, 255, 215, 0);
    private static final float REGION_LINE_THICKNESS = 2.0f;

    private static volatile AxiomBridgeService instance;

    private final Set<UUID> axiomPlayers = ConcurrentHashMap.newKeySet();
    private final Map<Long, MarkerHandle> handlesByModel = new ConcurrentHashMap<>();
    private final Map<Long, LightHandle> lightHandles = new ConcurrentHashMap<>();
    private final Map<UUID, MarkerAttachment> attachmentsByMarker = new ConcurrentHashMap<>();

    private AxiomBridgeService() {
    }

    public static AxiomBridgeService initialize() {
        if (instance != null) {
            return instance;
        }
        synchronized (AxiomBridgeService.class) {
            if (instance == null) {
                instance = new AxiomBridgeService();
                instance.registerListeners();
                LOGGER.info("Axiom bridge initialised with extended feature support");
            }
        }
        return instance;
    }

    public static AxiomBridgeService getInstance() {
        return instance;
    }

    private void registerListeners() {
        var eventHandler = MinecraftServer.getGlobalEventHandler();

        eventHandler.addListener(PlayerPluginMessageEvent.class, this::handlePluginMessage);
        eventHandler.addListener(PlayerDisconnectEvent.class, event -> axiomPlayers.remove(event.getPlayer().getUuid()));
        eventHandler.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                sendChannelRegistration(event.getPlayer());
            }
        });
    }

    private void sendChannelRegistration(Player player) {
        byte[] data = String.join("\0", SERVER_CHANNELS).getBytes(StandardCharsets.UTF_8);
        player.sendPluginMessage("minecraft:register", data);
        LOGGER.debug("Sent Axiom channel registration to {}", player.getUsername());
    }

    private void handlePluginMessage(PlayerPluginMessageEvent event) {
        String channel = event.getIdentifier();
        if (!channel.startsWith("axiom")) {
            return;
        }

        Player player = event.getPlayer();
        byte[] payload = event.getMessage();

        if (CHANNEL_HELLO.equals(channel)) {
            handleHello(player, payload);
            return;
        }

        if (CHANNEL_MANIPULATE_ENTITY.equals(channel)) {
            handleManipulateEntity(player, payload);
        }
    }

    private void handleHello(Player player, byte[] payload) {

        try {
            NetworkBuffer buffer = new NetworkBuffer(java.nio.ByteBuffer.wrap(payload));
            buffer.read(NetworkBuffer.VAR_INT);
            buffer.read(NetworkBuffer.VAR_INT);
            buffer.read(NetworkBuffer.VAR_INT);
        } catch (Exception ignored) {
        }

        axiomPlayers.add(player.getUuid());
        LOGGER.debug("Axiom editor enabled for {}", player.getUsername());

        player.sendPluginMessage(CHANNEL_ENABLE, EnableMessage.encode(
                true,
                ServerFlag.MAX_PACKET_SIZE,
                2,
                0,
                0
        ));

        EnumSet<AxiomPermission> allowed = EnumSet.allOf(AxiomPermission.class);
        EnumSet<AxiomPermission> denied = EnumSet.noneOf(AxiomPermission.class);
        player.sendPluginMessage(CHANNEL_RESTRICTIONS, RestrictionsMessage.encode(
                allowed,
                denied,
                -1,
                Set.of()
        ));

        sendAllMarkers(player);
    }

    private void handleManipulateEntity(Player player, byte[] payload) {
        List<ManipulateEntityMessage.Entry> entries;
        try {
            entries = ManipulateEntityMessage.parse(payload);
        } catch (Exception ex) {
            LOGGER.warn("Failed to decode Axiom manipulate packet from {}: {}", player.getUsername(), ex.getMessage());
            return;
        }

        boolean updated = false;
        for (ManipulateEntityMessage.Entry entry : entries) {
            MarkerAttachment attachment = attachmentsByMarker.get(entry.uuid());
            if (attachment == null || entry.position() == null) {
                continue;
            }

            Entity marker = attachment.marker();
            Pos current = marker.getPosition();
            Pos requested = entry.position();

            double x = entry.relative().contains(ManipulateEntityMessage.Relative.X) ? current.x() + requested.x() : requested.x();
            double y = entry.relative().contains(ManipulateEntityMessage.Relative.Y) ? current.y() + requested.y() : requested.y();
            double z = entry.relative().contains(ManipulateEntityMessage.Relative.Z) ? current.z() + requested.z() : requested.z();
            float yaw = entry.relative().contains(ManipulateEntityMessage.Relative.Y_ROT) ? current.yaw() + requested.yaw() : (float) requested.yaw();
            float pitch = entry.relative().contains(ManipulateEntityMessage.Relative.X_ROT) ? current.pitch() + requested.pitch() : (float) requested.pitch();

            Vector3 newPosition = new Vector3(x, y, z);
            attachment.onMarkerMoved(newPosition, yaw, pitch);
            scheduleNextTick(() -> syncMarkerAttachment(attachment));
            updated = true;
        }

        if (updated) {
            LOGGER.trace("Processed Axiom manipulation from {}", player.getUsername());
        }
    }

    public void onModelCreated(PlayerModelProxy model) {
        if (instance == null) {
            return;
        }
        scheduleNextTick(() -> createMarker(model));
    }

    public void onModelRemoved(PlayerModelProxy model) {
        if (instance == null) {
            return;
        }
        scheduleNextTick(() -> removeMarker(model));
    }

    public void onModelMoved(PlayerModelProxy model) {
        if (instance == null) {
            return;
        }
        scheduleNextTick(() -> syncMarkerWithModel(model));
    }

    public void onLightUpdated(long lightId, Map<String, Object> properties) {
        if (instance == null) {
            return;
        }
        scheduleNextTick(() -> updateLightHandle(lightId, properties));
    }

    public void onLightRemoved(long lightId) {
        if (instance == null) {
            return;
        }
        scheduleNextTick(() -> removeLightHandle(lightId));
    }

    private void createMarker(PlayerModelProxy model) {
        if (handlesByModel.containsKey(model.getModelId())) {
            return;
        }

        InstanceManager instanceManager = InstanceManager.getInstance();
        Instance instance = instanceManager.getDefaultInstance();
        if (instance == null) {
            LOGGER.warn("Cannot create Axiom gizmo for model {}: default instance is null", model.getModelId());
            return;
        }

        Entity marker = new Entity(EntityType.MARKER);
        marker.setNoGravity(true);
        marker.setTag(MarkerTags.NAME, "§ePlayer Model #" + model.getModelId());
        marker.setTag(MarkerTags.MIN, MarkerTags.stringVecToList("~-0.5", "~0.0", "~-0.5"));
        marker.setTag(MarkerTags.MAX, MarkerTags.stringVecToList("~0.5", "~2.0", "~0.5"));
        marker.setTag(MarkerTags.LINE_ARGB, REGION_LINE_COLOR);
        marker.setTag(MarkerTags.LINE_THICKNESS, REGION_LINE_THICKNESS);
        marker.setTag(MarkerTags.FACE_ARGB, REGION_FACE_COLOR);

        Pos position = toPos(model.getPosition());
        marker.setInstance(instance, position);

        MarkerHandle handle = new MarkerHandle(model, marker, instance);
        handlesByModel.put(model.getModelId(), handle);
        attachmentsByMarker.put(marker.getUuid(), handle);

        broadcastMarkerUpdate(List.of(handle), List.of());
    }

    private void removeMarker(PlayerModelProxy model) {
        MarkerHandle handle = handlesByModel.remove(model.getModelId());
        if (handle == null) {
            return;
        }
        attachmentsByMarker.remove(handle.marker().getUuid());
        handle.marker().remove();
        broadcastMarkerUpdate(List.of(), List.of(handle.marker().getUuid()));
    }

    private void updateLightHandle(long lightId, Map<String, Object> properties) {
        LightHandle handle = lightHandles.get(lightId);
        if (handle == null) {
            handle = createLightHandle(lightId, properties);
            if (handle == null) {
                return;
            }
            lightHandles.put(lightId, handle);
            attachmentsByMarker.put(handle.marker().getUuid(), handle);
        } else {
            handle.refreshProperties(properties);
            configureLightMarkerTags(handle);
        }

        syncMarkerWithLight(handle);
    }

    private void removeLightHandle(long lightId) {
        LightHandle handle = lightHandles.remove(lightId);
        if (handle == null) {
            return;
        }
        attachmentsByMarker.remove(handle.marker().getUuid());
        handle.marker().remove();
        broadcastMarkerUpdate(List.of(), List.of(handle.marker().getUuid()));
    }

    private LightHandle createLightHandle(long lightId, Map<String, Object> properties) {
        Instance instance = InstanceManager.getInstance().getDefaultInstance();
        if (instance == null) {
            LOGGER.warn("Cannot create Axiom light gizmo {}: default instance is null", lightId);
            return null;
        }

        Entity marker = new Entity(EntityType.MARKER);
        marker.setNoGravity(true);
        marker.setTag(MarkerTags.NAME, "§bLight #" + lightId);

        LightHandle handle = new LightHandle(lightId, marker, instance, properties);
        configureLightMarkerTags(handle);

        Vector3 position = handle.getPosition();
        marker.setInstance(instance, toPos(position));

        return handle;
    }

    private void configureLightMarkerTags(LightHandle handle) {
        Entity marker = handle.marker();
        double extent = Math.max(0.5, handle.estimateRadius() * 0.5);
        marker.setTag(MarkerTags.MIN, MarkerTags.stringVecToList(MarkerTags.relative(-extent), MarkerTags.relative(-0.2), MarkerTags.relative(-extent)));
        marker.setTag(MarkerTags.MAX, MarkerTags.stringVecToList(MarkerTags.relative(extent), MarkerTags.relative(extent), MarkerTags.relative(extent)));
        marker.setTag(MarkerTags.LINE_ARGB, handle.deriveColor());
        marker.setTag(MarkerTags.LINE_THICKNESS, REGION_LINE_THICKNESS);
        marker.setTag(MarkerTags.FACE_ARGB, handle.deriveColor() & 0x30ffffff);
    }

    private void syncMarkerWithModel(PlayerModelProxy model) {
        MarkerHandle handle = handlesByModel.get(model.getModelId());
        if (handle == null) {
            return;
        }
        syncMarkerWithModel(handle);
    }

    private void syncMarkerWithModel(MarkerHandle handle) {
        Entity marker = handle.marker();
        Vector3 position = handle.model().getPosition();
        Pos target = toPos(position);

        if (marker.getInstance() == null || !Objects.equals(marker.getInstance(), handle.instance())) {
            marker.setInstance(handle.instance(), target);
        } else {
            marker.teleport(target);
        }

        broadcastMarkerUpdate(List.of(handle), List.of());
    }

    private void syncMarkerWithLight(LightHandle handle) {
        Entity marker = handle.marker();
        Vector3 position = handle.getPosition();
        Pos target = toPos(position);

        if (marker.getInstance() == null || !Objects.equals(marker.getInstance(), handle.instance())) {
            marker.setInstance(handle.instance(), target);
        } else {
            marker.teleport(target);
        }

        broadcastMarkerUpdate(List.of(handle), List.of());
    }

    private void sendAllMarkers(Player player) {
        List<MarkerAttachment> attachments = new ArrayList<>();
        attachments.addAll(handlesByModel.values());
        attachments.addAll(lightHandles.values());
        if (attachments.isEmpty()) {
            return;
        }
        List<MarkerDataMessage.MarkerInfo> infos = attachments.stream()
                .map(this::toMarkerInfo)
                .toList();

        byte[] data = MarkerDataMessage.encode(infos, List.of());
        player.sendPluginMessage(CHANNEL_MARKER_DATA, data);
    }

    private void broadcastMarkerUpdate(Collection<? extends MarkerAttachment> changed, Collection<UUID> removed) {
        if (axiomPlayers.isEmpty()) {
            return;
        }

        List<MarkerDataMessage.MarkerInfo> infos = changed.stream()
                .map(this::toMarkerInfo)
                .toList();

        byte[] payload = MarkerDataMessage.encode(infos, removed);
        if (payload.length == 0 && removed.isEmpty()) {
            return;
        }

        List<UUID> stalePlayers = new ArrayList<>();
        var connectionManager = MinecraftServer.getConnectionManager();

        for (UUID uuid : axiomPlayers) {
            Player player = null;
            for (Player candidate : connectionManager.getOnlinePlayers()) {
                if (candidate.getUuid().equals(uuid)) {
                    player = candidate;
                    break;
                }
            }

            if (player == null) {
                stalePlayers.add(uuid);
                continue;
            }
            player.sendPluginMessage(CHANNEL_MARKER_DATA, payload);
        }

        axiomPlayers.removeAll(stalePlayers);
    }

    private MarkerDataMessage.MarkerInfo toMarkerInfo(MarkerAttachment attachment) {
        Entity marker = attachment.marker();
        Pos position = marker.getPosition();
        MarkerDataMessage.MarkerRegion region = attachment.computeRegion();
        return new MarkerDataMessage.MarkerInfo(marker.getUuid(), position, attachment.displayName(), region);
    }

    private Pos toPos(Vector3 vector) {
        return new Pos(vector.x, vector.y, vector.z);
    }

    private void scheduleNextTick(Runnable runnable) {
        MinecraftServer.getSchedulerManager()
                .scheduleTask(runnable, TaskSchedule.immediate(), TaskSchedule.stop());
    }

    private void syncMarkerAttachment(MarkerAttachment attachment) {
        if (attachment instanceof MarkerHandle markerHandle) {
            syncMarkerWithModel(markerHandle);
        } else if (attachment instanceof LightHandle lightHandle) {
            syncMarkerWithLight(lightHandle);
        }
    }

    private interface MarkerAttachment {
        Entity marker();
        Instance instance();
        String displayName();
        MarkerDataMessage.MarkerRegion computeRegion();
        void onMarkerMoved(Vector3 position, float yaw, float pitch);
    }

    private final class MarkerHandle implements MarkerAttachment {
        private final PlayerModelProxy model;
        private final Entity marker;
        private final Instance instance;

        private MarkerHandle(PlayerModelProxy model, Entity marker, Instance instance) {
            this.model = model;
            this.marker = marker;
            this.instance = instance;
        }

        @Override
        public Entity marker() {
            return marker;
        }

        @Override
        public Instance instance() {
            return instance;
        }

        @Override
        public String displayName() {
            return "§ePlayer Model #" + model.getModelId();
        }

        @Override
        public MarkerDataMessage.MarkerRegion computeRegion() {
            Pos position = marker.getPosition();
            Vec base = position.asVec();
            Vec min = base.add(REGION_OFFSET_MIN);
            Vec max = base.add(REGION_OFFSET_MAX);
            return new MarkerDataMessage.MarkerRegion(min, max, REGION_LINE_COLOR, REGION_LINE_THICKNESS, REGION_FACE_COLOR);
        }

        @Override
        public void onMarkerMoved(Vector3 position, float yaw, float pitch) {
            model.applyBridgeTransform(position, yaw, pitch);
        }
    }

    private final class LightHandle implements MarkerAttachment {
        private final long id;
        private final Entity marker;
        private final Instance instance;
        private Map<String, Object> properties;

        private LightHandle(long id, Entity marker, Instance instance, Map<String, Object> properties) {
            this.id = id;
            this.marker = marker;
            this.instance = instance;
            this.properties = properties;
        }

        void refreshProperties(Map<String, Object> properties) {
            this.properties = properties;
        }

        @Override
        public Entity marker() {
            return marker;
        }

        @Override
        public Instance instance() {
            return instance;
        }

        @Override
        public String displayName() {
            return "§bLight #" + id;
        }

        @Override
        public MarkerDataMessage.MarkerRegion computeRegion() {
            Vector3 pos = getPosition();
            Vec base = new Vec(pos.x, pos.y, pos.z);
            double radius = estimateRadius();
            double half = Math.max(0.4, radius * 0.5);
            Vec min = base.add(-half, -0.2, -half);
            Vec max = base.add(half, half, half);

            int lineColor = deriveColor();
            return new MarkerDataMessage.MarkerRegion(min, max, lineColor, REGION_LINE_THICKNESS, lineColor & 0x40ffffff);
        }

        @Override
        public void onMarkerMoved(Vector3 position, float yaw, float pitch) {
            properties.put("x", position.x);
            properties.put("y", position.y);
            properties.put("z", position.z);
            ServerLightingManager.getInstance().createOrUpdateLight(id, Map.of(
                    "x", position.x,
                    "y", position.y,
                    "z", position.z
            ));
        }

        Vector3 getPosition() {
            double x = getDouble("x", marker.getPosition().x());
            double y = getDouble("y", marker.getPosition().y());
            double z = getDouble("z", marker.getPosition().z());
            return new Vector3(x, y, z);
        }

        double estimateRadius() {
            if ("area".equalsIgnoreCase(getString("type", ""))) {
                double width = getDouble("width", 3);
                double height = getDouble("height", 3);
                return Math.max(width, height);
            }
            return getDouble("radius", 3);
        }

        int deriveColor() {
            double r = getDouble("r", 1.0);
            double g = getDouble("g", 0.8);
            double b = getDouble("b", 0.6);
            int red = clampColor(r);
            int green = clampColor(g);
            int blue = clampColor(b);
            return MarkerTags.argbToInt(255, red, green, blue);
        }

        private int clampColor(double component) {
            return Math.max(0, Math.min(255, (int) Math.round(component * 255.0)));
        }

        private double getDouble(String key, double fallback) {
            Object value = properties.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return fallback;
        }

        private String getString(String key, String fallback) {
            Object value = properties.get(key);
            return value != null ? value.toString() : fallback;
        }
    }

    private static final class MarkerTags {
        private static final Tag<String> NAME = Tag.String("name");
        private static final Tag<Float> LINE_THICKNESS = Tag.Float("line_thickness");
        private static final Tag<Integer> LINE_ARGB = Tag.Integer("line_argb");
        private static final Tag<Integer> FACE_ARGB = Tag.Integer("face_argb");
        private static final Tag<List<String>> MIN = Tag.String("min").list();
        private static final Tag<List<String>> MAX = Tag.String("max").list();

        private MarkerTags() {
        }

        static List<String> stringVecToList(String x, String y, String z) {
            return List.of(x, y, z);
        }

        static int argbToInt(int alpha, int red, int green, int blue) {
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }

        static String relative(double value) {
            return String.format("~%.2f", value);
        }
    }
}
