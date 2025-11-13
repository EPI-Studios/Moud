package com.moud.server.bridge;

import com.moud.api.math.Quaternion;
import com.moud.api.math.Vector3;
import com.moud.server.entity.ModelManager;
import com.moud.server.instance.InstanceManager;
import com.moud.server.lighting.ServerLightingManager;
import com.moud.server.logging.MoudLogger;
import com.moud.network.MoudPackets;
import com.moud.server.proxy.MediaDisplayProxy;
import com.moud.server.proxy.ModelProxy;
import com.moud.server.proxy.PlayerModelProxy;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.LongBinaryTag;
import net.kyori.adventure.nbt.ShortBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class AxiomBridgeService {

    private static final MoudLogger LOGGER = MoudLogger.getLogger(AxiomBridgeService.class);

    private static final String CHANNEL_HELLO = "axiom:hello";
    private static final String CHANNEL_ENABLE = "axiom:enable";
    private static final String CHANNEL_RESTRICTIONS = "axiom:restrictions";
    private static final String CHANNEL_MARKER_DATA = "axiom:marker_data";
    private static final String CHANNEL_MANIPULATE_ENTITY = "axiom:manipulate_entity";
    private static final String CHANNEL_MARKER_NBT_REQUEST = "axiom:marker_nbt_request";
    private static final String CHANNEL_MARKER_NBT_RESPONSE = "axiom:marker_nbt_response";

    private static final List<String> SERVER_CHANNELS = List.of(
            CHANNEL_HELLO,
            CHANNEL_ENABLE,
            CHANNEL_RESTRICTIONS,
            CHANNEL_MARKER_DATA,
            CHANNEL_MANIPULATE_ENTITY,
            CHANNEL_MARKER_NBT_REQUEST,
            CHANNEL_MARKER_NBT_RESPONSE
    );

    private static final Vec REGION_OFFSET_MIN = new Vec(-0.5, 0.0, -0.5);
    private static final Vec REGION_OFFSET_MAX = new Vec(0.5, 2.0, 0.5);
    private static final int REGION_LINE_COLOR = MarkerTags.argbToInt(255, 255, 215, 0);
    private static final int REGION_FACE_COLOR = MarkerTags.argbToInt(40, 255, 215, 0);
    private static final float REGION_LINE_THICKNESS = 2.0f;
    private static final Pattern LEGACY_FORMATTING = Pattern.compile("§[0-9A-FK-ORa-fk-or]");

    private static volatile AxiomBridgeService instance;

    private final Set<UUID> axiomPlayers = ConcurrentHashMap.newKeySet();
    private final Map<Long, MarkerHandle> handlesByModel = new ConcurrentHashMap<>();
    private final Map<Long, ModelMarkerHandle> staticModelHandles = new ConcurrentHashMap<>();
    private final Map<Long, DisplayMarkerHandle> displayHandles = new ConcurrentHashMap<>();
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
                ModelManager.getInstance().getAllModels().forEach(model -> instance.scheduleNextTick(() -> instance.createModelMarker(model)));
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

        if (CHANNEL_MARKER_NBT_REQUEST.equals(channel)) {
            handleMarkerNbtRequest(player, payload);
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

    private void handleMarkerNbtRequest(Player player, byte[] payload) {
        MarkerNbtRequestMessage.Request request;
        try {
            request = MarkerNbtRequestMessage.parse(payload);
        } catch (Exception ex) {
            LOGGER.warn("Failed to decode Axiom marker NBT request from {}: {}", player.getUsername(), ex.getMessage());
            return;
        }

        MarkerAttachment attachment = attachmentsByMarker.get(request.uuid());
        if (attachment == null) {
            LOGGER.trace("Ignoring marker NBT request for unknown marker {} from {}", request.uuid(), player.getUsername());
            return;
        }

        CompoundBinaryTag data = buildMarkerNbt(attachment);
        byte[] response = MarkerNbtResponseMessage.encode(request.uuid(), data);
        player.sendPluginMessage(CHANNEL_MARKER_NBT_RESPONSE, response);
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
        List<MarkerAttachment> changedAttachments = new ArrayList<>();
        for (ManipulateEntityMessage.Entry entry : entries) {
            MarkerAttachment attachment = attachmentsByMarker.get(entry.uuid());
            if (attachment == null) {
                continue;
            }

            Entity marker = attachment.marker();
            Pos current = marker.getPosition();
            boolean hasPosition = entry.position() != null;
            BinaryTag metadata = unwrapMetadata(entry.nbt());
            boolean hasMetadata = metadata != null && !(metadata instanceof CompoundBinaryTag compound && compound.size() == 0);

            if (!hasPosition && !hasMetadata) {
                continue;
            }

            Vector3 newPosition;
            float yaw;
            float pitch;

            if (hasPosition) {
                Pos requested = entry.position();
                double x = entry.relative().contains(ManipulateEntityMessage.Relative.X) ? current.x() + requested.x() : requested.x();
                double y = entry.relative().contains(ManipulateEntityMessage.Relative.Y) ? current.y() + requested.y() : requested.y();
                double z = entry.relative().contains(ManipulateEntityMessage.Relative.Z) ? current.z() + requested.z() : requested.z();
                yaw = entry.relative().contains(ManipulateEntityMessage.Relative.Y_ROT) ? current.yaw() + requested.yaw() : (float) requested.yaw();
                pitch = entry.relative().contains(ManipulateEntityMessage.Relative.X_ROT) ? current.pitch() + requested.pitch() : (float) requested.pitch();
                newPosition = new Vector3(x, y, z);
            } else {
                yaw = current.yaw();
                pitch = current.pitch();
                newPosition = new Vector3(current.x(), current.y(), current.z());
            }

            attachment.onMarkerMoved(newPosition, yaw, pitch, metadata);
            changedAttachments.add(attachment);
            updated = true;
        }

        if (updated) {
            changedAttachments.forEach(this::alignMarkerAttachment);
            LOGGER.trace("Processed Axiom manipulation from {}", player.getUsername());
            broadcastMarkerUpdate(changedAttachments, List.of());
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

    public void onStaticModelCreated(ModelProxy model) {
        if (instance == null) {
            return;
        }
        scheduleNextTick(() -> createModelMarker(model));
    }

    public void onStaticModelRemoved(ModelProxy model) {
        if (instance == null) {
            return;
        }
        scheduleNextTick(() -> removeModelMarker(model));
    }

    public void onStaticModelMoved(ModelProxy model) {
        if (instance == null) {
            return;
        }
        scheduleNextTick(() -> syncMarkerWithModel(model));
    }

    public void onDisplayCreated(MediaDisplayProxy display) {
        if (instance == null) {
            return;
        }
        scheduleNextTick(() -> createDisplayMarker(display));
    }

    public void onDisplayRemoved(MediaDisplayProxy display) {
        if (instance == null) {
            return;
        }
        scheduleNextTick(() -> removeDisplayMarker(display));
    }

    public void onDisplayMoved(MediaDisplayProxy display) {
        if (instance == null) {
            return;
        }
        scheduleNextTick(() -> syncMarkerWithDisplay(display));
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

    private void createModelMarker(ModelProxy model) {
        if (staticModelHandles.containsKey(model.getId())) {
            return;
        }

        InstanceManager instanceManager = InstanceManager.getInstance();
        Instance instance = instanceManager.getDefaultInstance();
        if (instance == null) {
            LOGGER.warn("Cannot create Axiom gizmo for model {}: default instance is null", model.getId());
            return;
        }

        Entity marker = new Entity(EntityType.MARKER);
        marker.setNoGravity(true);
        marker.setTag(MarkerTags.NAME, "§dModel #" + model.getId());

        Vector3 scale = model.getScale();
        double halfX = Math.max(0.5, scale.x / 2.0);
        double halfZ = Math.max(0.5, scale.z / 2.0);
        double height = Math.max(1.0, scale.y);

        marker.setTag(MarkerTags.MIN, MarkerTags.stringVecToList(MarkerTags.relative(-halfX), MarkerTags.relative(0.0), MarkerTags.relative(-halfZ)));
        marker.setTag(MarkerTags.MAX, MarkerTags.stringVecToList(MarkerTags.relative(halfX), MarkerTags.relative(height), MarkerTags.relative(halfZ)));
        marker.setTag(MarkerTags.LINE_ARGB, MarkerTags.argbToInt(255, 102, 153, 255));
        marker.setTag(MarkerTags.LINE_THICKNESS, REGION_LINE_THICKNESS);
        marker.setTag(MarkerTags.FACE_ARGB, MarkerTags.argbToInt(50, 102, 153, 255));

        Pos position = toPos(model.getPosition());
        marker.setInstance(instance, position);

        ModelMarkerHandle handle = new ModelMarkerHandle(model, marker, instance);
        staticModelHandles.put(model.getId(), handle);
        attachmentsByMarker.put(marker.getUuid(), handle);

        broadcastMarkerUpdate(List.of(handle), List.of());
    }

    private void createDisplayMarker(MediaDisplayProxy display) {
        if (displayHandles.containsKey(display.getId())) {
            return;
        }

        InstanceManager instanceManager = InstanceManager.getInstance();
        Instance instance = instanceManager.getDefaultInstance();
        if (instance == null) {
            LOGGER.warn("Cannot create Axiom gizmo for display {}: default instance is null", display.getId());
            return;
        }

        Entity marker = new Entity(EntityType.MARKER);
        marker.setNoGravity(true);
        marker.setTag(MarkerTags.NAME, "§bDisplay #" + display.getId());

        Vector3 scale = display.getScale();
        double halfX = Math.max(0.5, scale.x / 2.0);
        double halfY = Math.max(0.5, scale.y / 2.0);
        double halfZ = Math.max(0.25, scale.z / 2.0);

        marker.setTag(MarkerTags.MIN, MarkerTags.stringVecToList(
                MarkerTags.relative(-halfX), MarkerTags.relative(-halfY), MarkerTags.relative(-halfZ)
        ));
        marker.setTag(MarkerTags.MAX, MarkerTags.stringVecToList(
                MarkerTags.relative(halfX), MarkerTags.relative(halfY), MarkerTags.relative(halfZ)
        ));
        marker.setTag(MarkerTags.LINE_ARGB, MarkerTags.argbToInt(255, 100, 220, 255));
        marker.setTag(MarkerTags.LINE_THICKNESS, REGION_LINE_THICKNESS);
        marker.setTag(MarkerTags.FACE_ARGB, MarkerTags.argbToInt(50, 100, 220, 255));

        Pos position = toPos(display.getPosition());
        marker.setInstance(instance, position);

        DisplayMarkerHandle handle = new DisplayMarkerHandle(display, marker, instance);
        displayHandles.put(display.getId(), handle);
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

    private void removeModelMarker(ModelProxy model) {
        ModelMarkerHandle handle = staticModelHandles.remove(model.getId());
        if (handle == null) {
            return;
        }
        attachmentsByMarker.remove(handle.marker().getUuid());
        handle.marker().remove();
        broadcastMarkerUpdate(List.of(), List.of(handle.marker().getUuid()));
    }

    private void removeDisplayMarker(MediaDisplayProxy display) {
        DisplayMarkerHandle handle = displayHandles.remove(display.getId());
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
        Vector3 position = handle.model.getPosition();
        Pos target = toPos(position);

        if (marker.getInstance() == null || !Objects.equals(marker.getInstance(), handle.instance())) {
            marker.setInstance(handle.instance(), target);
        } else {
            if (marker.getChunk() != null) {
                marker.teleport(target);
            }
        }

        broadcastMarkerUpdate(List.of(handle), List.of());
    }


    private void syncMarkerWithModel(ModelProxy model) {
        ModelMarkerHandle handle = staticModelHandles.get(model.getId());
        if (handle == null) {
            return;
        }
        syncMarkerWithModel(handle);
    }

    private void syncMarkerWithModel(ModelMarkerHandle handle) {
        Entity marker = handle.marker();
        Vector3 position = handle.model.getPosition();
        Pos target = toPos(position);

        if (marker.getInstance() == null || !Objects.equals(marker.getInstance(), handle.instance())) {
            marker.setInstance(handle.instance(), target);
        } else {
            marker.teleport(target);
        }

        handle.refreshRegionBounds();
        broadcastMarkerUpdate(List.of(handle), List.of());
    }

    private void syncMarkerWithDisplay(MediaDisplayProxy display) {
        DisplayMarkerHandle handle = displayHandles.get(display.getId());
        if (handle == null) {
            return;
        }
        syncDisplayMarker(handle);
    }

    private void syncDisplayMarker(DisplayMarkerHandle handle) {
        Entity marker = handle.marker();
        Vector3 position = handle.display.getPosition();
        Pos target = toPos(position);

        if (marker.getInstance() == null || !Objects.equals(marker.getInstance(), handle.instance())) {
            marker.setInstance(handle.instance(), target);
        } else {
            marker.teleport(target);
        }

        handle.refreshRegionBounds();
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
        attachments.addAll(staticModelHandles.values());
        attachments.addAll(displayHandles.values());
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

    private CompoundBinaryTag buildMarkerNbt(MarkerAttachment attachment) {
        CompoundBinaryTag.Builder builder = CompoundBinaryTag.builder();
        String displayName = sanitizeDisplayName(attachment.displayName());
        if (!displayName.isEmpty()) {
            builder.putString("name", displayName);
        }

        MarkerDataMessage.MarkerRegion region = attachment.computeRegion();
        if (region != null) {
            Pos origin = attachment.marker().getPosition();
            builder.put("min", relativeList(region.min(), origin));
            builder.put("max", relativeList(region.max(), origin));
        }

        String markerType = attachment.markerType();
        if (!markerType.isEmpty()) {
            builder.putString("moudType", markerType);
        }

        attachment.writeCustomMetadata(builder);
        return builder.build();
    }

    private ListBinaryTag relativeList(Vec absolute, Pos origin) {
        double rx = absolute.x() - origin.x();
        double ry = absolute.y() - origin.y();
        double rz = absolute.z() - origin.z();
        return ListBinaryTag.listBinaryTag(
                BinaryTagTypes.STRING,
                List.of(
                        StringBinaryTag.stringBinaryTag(MarkerTags.relative(rx)),
                        StringBinaryTag.stringBinaryTag(MarkerTags.relative(ry)),
                        StringBinaryTag.stringBinaryTag(MarkerTags.relative(rz))
                )
        );
    }

    private String sanitizeDisplayName(String raw) {
        if (raw == null) {
            return "";
        }
        String stripped = LEGACY_FORMATTING.matcher(raw).replaceAll("");
        return stripped.strip();
    }

    private BinaryTag unwrapMetadata(BinaryTag metadata) {
        if (metadata instanceof CompoundBinaryTag compound) {
            BinaryTag dataTag = compound.get("data");
            if (dataTag instanceof CompoundBinaryTag inner) {
                return inner;
            }
        }
        return metadata;
    }

    private void alignMarkerAttachment(MarkerAttachment attachment) {
        if (attachment instanceof MarkerHandle markerHandle) {
            alignMarker(markerHandle);
        } else if (attachment instanceof ModelMarkerHandle modelHandle) {
            alignMarker(modelHandle);
        } else if (attachment instanceof DisplayMarkerHandle displayHandle) {
            alignDisplayMarker(displayHandle);
        } else if (attachment instanceof LightHandle lightHandle) {
            alignMarker(lightHandle);
        }
    }

    private void alignMarker(MarkerHandle handle) {
        Entity marker = handle.marker();
        Vector3 position = handle.model.getPosition();
        Pos target = toPos(position);

        if (marker.getInstance() == null || !Objects.equals(marker.getInstance(), handle.instance())) {
            marker.setInstance(handle.instance(), target);
        } else {
            marker.teleport(target);
        }
    }

    private void alignMarker(ModelMarkerHandle handle) {
        Entity marker = handle.marker();
        Vector3 position = handle.model.getPosition();
        Pos target = toPos(position);

        if (marker.getInstance() == null || !Objects.equals(marker.getInstance(), handle.instance())) {
            marker.setInstance(handle.instance(), target);
        } else {
            marker.teleport(target);
        }

        handle.refreshRegionBounds();
    }

    private void alignDisplayMarker(DisplayMarkerHandle handle) {
        Entity marker = handle.marker();
        Vector3 position = handle.display.getPosition();
        Pos target = toPos(position);

        if (marker.getInstance() == null || !Objects.equals(marker.getInstance(), handle.instance())) {
            marker.setInstance(handle.instance(), target);
        } else {
            marker.teleport(target);
        }

        handle.refreshRegionBounds();
    }

    private void alignMarker(LightHandle handle) {
        Entity marker = handle.marker();
        Vector3 position = handle.getPosition();
        Pos target = toPos(position);

        if (marker.getInstance() == null || !Objects.equals(marker.getInstance(), handle.instance())) {
            marker.setInstance(handle.instance(), target);
        } else {
            marker.teleport(target);
        }

        configureLightMarkerTags(handle);
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
        } else if (attachment instanceof ModelMarkerHandle modelHandle) {
            syncMarkerWithModel(modelHandle);
        } else if (attachment instanceof DisplayMarkerHandle displayHandle) {
            syncDisplayMarker(displayHandle);
        } else if (attachment instanceof LightHandle lightHandle) {
            syncMarkerWithLight(lightHandle);
        }
    }

    private interface MarkerAttachment {
        Entity marker();
        Instance instance();
        String displayName();
        MarkerDataMessage.MarkerRegion computeRegion();
        void onMarkerMoved(Vector3 position, float yaw, float pitch, BinaryTag metadata);
        default void writeCustomMetadata(CompoundBinaryTag.Builder builder) {
        }
        default String markerType() {
            return "";
        }
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
        public void onMarkerMoved(Vector3 position, float yaw, float pitch, BinaryTag metadata) {
            if (metadata instanceof CompoundBinaryTag compound) {
                double yawValue = readNumeric(compound, "yaw");
                double pitchValue = readNumeric(compound, "pitch");
                if (!Double.isNaN(yawValue)) {
                    yaw = (float) yawValue;
                }
                if (!Double.isNaN(pitchValue)) {
                    pitch = (float) pitchValue;
                }
            }
            model.applyBridgeTransform(position, yaw, pitch);
        }

        @Override
        public void writeCustomMetadata(CompoundBinaryTag.Builder builder) {
            builder.putFloat("yaw", model.getYaw());
            builder.putFloat("pitch", model.getPitch());
        }

        @Override
        public String markerType() {
            return "player_model";
        }
    }

    private final class ModelMarkerHandle implements MarkerAttachment {
        private final ModelProxy model;
        private final Entity marker;
        private final Instance instance;

        private ModelMarkerHandle(ModelProxy model, Entity marker, Instance instance) {
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
            return "§dModel #" + model.getId();
        }

        @Override
        public MarkerDataMessage.MarkerRegion computeRegion() {
            Vector3 scale = model.getScale();
            double halfX = Math.max(0.5, scale.x / 2.0);
            double halfZ = Math.max(0.5, scale.z / 2.0);
            double height = Math.max(1.0, scale.y);

            Pos position = marker.getPosition();
            Vec base = position.asVec();
            Vec min = base.add(-halfX, 0.0, -halfZ);
            Vec max = base.add(halfX, height, halfZ);
            int lineColor = MarkerTags.argbToInt(255, 102, 153, 255);
            int faceColor = MarkerTags.argbToInt(60, 102, 153, 255);
            return new MarkerDataMessage.MarkerRegion(min, max, lineColor, REGION_LINE_THICKNESS, faceColor);
        }

        void refreshRegionBounds() {
            Vector3 scale = model.getScale();
            double halfX = Math.max(0.5, scale.x / 2.0);
            double halfZ = Math.max(0.5, scale.z / 2.0);
            double height = Math.max(1.0, scale.y);
            marker.setTag(MarkerTags.MIN, MarkerTags.stringVecToList(MarkerTags.relative(-halfX), MarkerTags.relative(0.0), MarkerTags.relative(-halfZ)));
            marker.setTag(MarkerTags.MAX, MarkerTags.stringVecToList(MarkerTags.relative(halfX), MarkerTags.relative(height), MarkerTags.relative(halfZ)));
        }

        @Override
        public void onMarkerMoved(Vector3 position, float yaw, float pitch, BinaryTag metadata) {
            Quaternion rotation = quaternionFromYawPitch(yaw, pitch);
            Vector3 scale = extractScale(metadata, model.getScale());
            rotation = extractRotation(metadata, rotation);
            model.applyBridgeTransform(position, rotation, scale);
            String texture = extractTexture(metadata);
            if (texture != null && !texture.isEmpty()) {
                model.setTexture(texture);
            }
            refreshRegionBounds();
        }

        @Override
        public void writeCustomMetadata(CompoundBinaryTag.Builder builder) {
            Vector3 scale = model.getScale();
            builder.putDouble("scaleX", scale.x);
            builder.putDouble("scaleY", scale.y);
            builder.putDouble("scaleZ", scale.z);
            builder.put("scale", CompoundBinaryTag.builder()
                    .putDouble("x", scale.x)
                    .putDouble("y", scale.y)
                    .putDouble("z", scale.z)
                    .build());
            Quaternion rotation = model.getRotation();
            if (rotation != null) {
                Vector3 euler = rotation.toEuler();
                builder.putDouble("rotationPitch", euler.x);
                builder.putDouble("rotationYaw", euler.y);
                builder.putDouble("rotationRoll", euler.z);
                builder.put("rotation", CompoundBinaryTag.builder()
                        .putDouble("pitch", euler.x)
                        .putDouble("yaw", euler.y)
                        .putDouble("roll", euler.z)
                        .build());
                builder.put("rotationQuat", CompoundBinaryTag.builder()
                        .putDouble("x", rotation.x)
                        .putDouble("y", rotation.y)
                        .putDouble("z", rotation.z)
                        .putDouble("w", rotation.w)
                        .build());
            }
            Vector3 position = model.getPosition();
            builder.putDouble("positionX", position.x);
            builder.putDouble("positionY", position.y);
            builder.putDouble("positionZ", position.z);
            builder.putString("modelPath", model.getModelPath());
            builder.putString("texture", model.getTexture());
        }

        @Override
        public String markerType() {
            return "model";
        }
    }

    private final class DisplayMarkerHandle implements MarkerAttachment {
        private final MediaDisplayProxy display;
        private final Entity marker;
        private final Instance instance;

        private DisplayMarkerHandle(MediaDisplayProxy display, Entity marker, Instance instance) {
            this.display = display;
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
            return "§bDisplay #" + display.getId();
        }

        @Override
        public MarkerDataMessage.MarkerRegion computeRegion() {
            Vector3 scale = display.getScale();
            double halfX = Math.max(0.5, scale.x / 2.0);
            double halfY = Math.max(0.5, scale.y / 2.0);
            double halfZ = Math.max(0.25, scale.z / 2.0);

            Pos position = marker.getPosition();
            Vec base = position.asVec();
            Vec min = base.add(-halfX, -halfY, -halfZ);
            Vec max = base.add(halfX, halfY, halfZ);
            int lineColor = MarkerTags.argbToInt(255, 100, 220, 255);
            int faceColor = MarkerTags.argbToInt(50, 100, 220, 255);
            return new MarkerDataMessage.MarkerRegion(min, max, lineColor, REGION_LINE_THICKNESS, faceColor);
        }

        void refreshRegionBounds() {
            Vector3 scale = display.getScale();
            double halfX = Math.max(0.5, scale.x / 2.0);
            double halfY = Math.max(0.5, scale.y / 2.0);
            double halfZ = Math.max(0.25, scale.z / 2.0);

            marker.setTag(MarkerTags.MIN, MarkerTags.stringVecToList(
                    MarkerTags.relative(-halfX), MarkerTags.relative(-halfY), MarkerTags.relative(-halfZ)
            ));
            marker.setTag(MarkerTags.MAX, MarkerTags.stringVecToList(
                    MarkerTags.relative(halfX), MarkerTags.relative(halfY), MarkerTags.relative(halfZ)
            ));

            if (display.getContentType() == MoudPackets.DisplayContentType.IMAGE || display.getContentType() == MoudPackets.DisplayContentType.URL) {
                String texture = display.getPrimarySource();
                if (texture != null && !texture.isEmpty()) {
                    marker.setTag(Tag.String("image"), texture);
                }
            }
        }

        @Override
        public void onMarkerMoved(Vector3 position, float yaw, float pitch, BinaryTag metadata) {
            Quaternion rotation = quaternionFromYawPitch(yaw, pitch);
            Vector3 scale = extractScale(metadata, display.getScale());
            rotation = extractRotation(metadata, rotation);
            display.applyBridgeTransform(position, rotation, scale);
            refreshRegionBounds();
        }

        @Override
        public void writeCustomMetadata(CompoundBinaryTag.Builder builder) {
            Vector3 scale = display.getScale();
            builder.putDouble("scaleX", scale.x);
            builder.putDouble("scaleY", scale.y);
            builder.putDouble("scaleZ", scale.z);
            builder.put("scale", CompoundBinaryTag.builder()
                    .putDouble("x", scale.x)
                    .putDouble("y", scale.y)
                    .putDouble("z", scale.z)
                    .build());
            Quaternion rotation = display.getRotation();
            if (rotation != null) {
                Vector3 euler = rotation.toEuler();
                builder.putDouble("rotationPitch", euler.x);
                builder.putDouble("rotationYaw", euler.y);
                builder.putDouble("rotationRoll", euler.z);
                builder.put("rotation", CompoundBinaryTag.builder()
                        .putDouble("pitch", euler.x)
                        .putDouble("yaw", euler.y)
                        .putDouble("roll", euler.z)
                        .build());
                builder.put("rotationQuat", CompoundBinaryTag.builder()
                        .putDouble("x", rotation.x)
                        .putDouble("y", rotation.y)
                        .putDouble("z", rotation.z)
                        .putDouble("w", rotation.w)
                        .build());
            }
            Vector3 position = display.getPosition();
            builder.putDouble("positionX", position.x);
            builder.putDouble("positionY", position.y);
            builder.putDouble("positionZ", position.z);
            builder.putString("contentType", display.getContentType().name());
            if (display.getContentType() == MoudPackets.DisplayContentType.IMAGE || display.getContentType() == MoudPackets.DisplayContentType.URL) {
                builder.putString("source", display.getPrimarySource());
            }
        }

        @Override
        public String markerType() {
            return "display";
        }
    }

    private Quaternion quaternionFromYawPitch(float yawDegrees, float pitchDegrees) {
        Quaternion yawRotation = Quaternion.fromAxisAngle(Vector3.up(), yawDegrees);
        Quaternion pitchRotation = Quaternion.fromAxisAngle(Vector3.right(), pitchDegrees);
        return yawRotation.multiply(pitchRotation);
    }

    private Vector3 extractScale(BinaryTag metadata, Vector3 fallback) {
        if (!(metadata instanceof CompoundBinaryTag compound)) {
            return fallback;
        }

        double sx = fallback.x;
        double sy = fallback.y;
        double sz = fallback.z;
        boolean changed = false;

        double candidate = readNumeric(compound, "scaleX");
        if (!Double.isNaN(candidate)) {
            sx = candidate;
            changed = true;
        }
        candidate = readNumeric(compound, "scaleY");
        if (!Double.isNaN(candidate)) {
            sy = candidate;
            changed = true;
        }
        candidate = readNumeric(compound, "scaleZ");
        if (!Double.isNaN(candidate)) {
            sz = candidate;
            changed = true;
        }

        candidate = readNumeric(compound, "scale_x");
        if (!Double.isNaN(candidate)) {
            sx = candidate;
            changed = true;
        }
        candidate = readNumeric(compound, "scale_y");
        if (!Double.isNaN(candidate)) {
            sy = candidate;
            changed = true;
        }
        candidate = readNumeric(compound, "scale_z");
        if (!Double.isNaN(candidate)) {
            sz = candidate;
            changed = true;
        }

        BinaryTag scaleTag = compound.get("scale");
        if (scaleTag instanceof CompoundBinaryTag nested) {
            double x = readNumeric(nested, "x");
            double y = readNumeric(nested, "y");
            double z = readNumeric(nested, "z");
            if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z)) {
                sx = x;
                sy = y;
                sz = z;
                changed = true;
            }
        } else if (scaleTag instanceof ListBinaryTag list && list.size() >= 3) {
            double[] values = readListScale(list);
            if (values != null) {
                sx = values[0];
                sy = values[1];
                sz = values[2];
                changed = true;
            }
        }

        if (!changed) {
            return fallback;
        }
        return new Vector3(sx, sy, sz);
    }

    private Quaternion extractRotation(BinaryTag metadata, Quaternion fallback) {
        if (!(metadata instanceof CompoundBinaryTag compound)) {
            return fallback;
        }

        CompoundBinaryTag target = compound;
        BinaryTag nested = compound.get("rotation");
        Quaternion rotation = fallback;

        double pitchVal = readNumeric(target, "rotationPitch");
        double yawVal = readNumeric(target, "rotationYaw");
        double rollVal = readNumeric(target, "rotationRoll");

        if (!(nested instanceof CompoundBinaryTag) && (Double.isNaN(pitchVal) && Double.isNaN(yawVal) && Double.isNaN(rollVal))) {
            nested = compound.get("rotationQuat");
        }

        if (!Double.isNaN(pitchVal) || !Double.isNaN(yawVal) || !Double.isNaN(rollVal)) {
            Vector3 eulerFallback = fallback != null ? fallback.toEuler() : Vector3.zero();
            float pitch = (float) (Double.isNaN(pitchVal) ? eulerFallback.x : pitchVal);
            float yaw = (float) (Double.isNaN(yawVal) ? eulerFallback.y : yawVal);
            float roll = (float) (Double.isNaN(rollVal) ? eulerFallback.z : rollVal);
            rotation = Quaternion.fromEuler(pitch, yaw, roll);
        } else if (nested instanceof CompoundBinaryTag eulerTag) {
            double pitch = readNumeric(eulerTag, "pitch");
            double yaw = readNumeric(eulerTag, "yaw");
            double roll = readNumeric(eulerTag, "roll");
            if (!Double.isNaN(pitch) || !Double.isNaN(yaw) || !Double.isNaN(roll)) {
                Vector3 eulerFallback = fallback != null ? fallback.toEuler() : Vector3.zero();
                float p = (float) (Double.isNaN(pitch) ? eulerFallback.x : pitch);
                float y = (float) (Double.isNaN(yaw) ? eulerFallback.y : yaw);
                float r = (float) (Double.isNaN(roll) ? eulerFallback.z : roll);
                rotation = Quaternion.fromEuler(p, y, r);
            }
        } else if (nested instanceof CompoundBinaryTag quatTag) {
            double x = readNumeric(quatTag, "x");
            double y = readNumeric(quatTag, "y");
            double z = readNumeric(quatTag, "z");
            double w = readNumeric(quatTag, "w");
            if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z) && !Double.isNaN(w)) {
                rotation = new Quaternion((float) x, (float) y, (float) z, (float) w);
            }
        }

        return rotation;
    }

    private String extractTexture(BinaryTag metadata) {
        if (!(metadata instanceof CompoundBinaryTag compound)) {
            return null;
        }
        BinaryTag tag = compound.get("texture");
        if (tag instanceof StringBinaryTag stringTag) {
            return stringTag.value();
        }
        return null;
    }

    private void mergeLightMetadata(BinaryTag metadata, Map<String, Object> target, Map<String, Object> changed) {
        if (!(metadata instanceof CompoundBinaryTag compound)) {
            return;
        }

        mergeStringField(compound, target, changed, "type");

        mergeNumericField(compound, target, changed, "radius");
        mergeNumericField(compound, target, changed, "width");
        mergeNumericField(compound, target, changed, "height");
        mergeNumericField(compound, target, changed, "distance");
        mergeNumericField(compound, target, changed, "angle");
        mergeNumericField(compound, target, changed, "brightness");

        mergeNumericField(compound, target, changed, "r");
        mergeNumericField(compound, target, changed, "g");
        mergeNumericField(compound, target, changed, "b");

        mergeNumericField(compound, target, changed, "dirX");
        mergeNumericField(compound, target, changed, "dirY");
        mergeNumericField(compound, target, changed, "dirZ");
    }

    private void mergeStringField(CompoundBinaryTag compound, Map<String, Object> target, Map<String, Object> changed, String key) {
        BinaryTag tag = compound.get(key);
        if (tag instanceof StringBinaryTag stringTag) {
            String value = stringTag.value();
            target.put(key, value);
            changed.put(key, value);
        }
    }

    private void mergeNumericField(CompoundBinaryTag compound, Map<String, Object> target, Map<String, Object> changed, String key, String... aliases) {
        double value = readNumeric(compound, key);
        if (Double.isNaN(value)) {
            for (String alias : aliases) {
                value = readNumeric(compound, alias);
                if (!Double.isNaN(value)) {
                    key = alias;
                    break;
                }
            }
        }

        if (Double.isNaN(value)) {
            return;
        }

        if ("r".equalsIgnoreCase(key) || "g".equalsIgnoreCase(key) || "b".equalsIgnoreCase(key)) {
            if (value > 1.0) {
                value = value / 255.0;
            }
        }

        target.put(key, value);
        changed.put(key, value);
    }

    private double[] readListScale(ListBinaryTag list) {
        double[] values = new double[3];
        int index = 0;
        for (BinaryTag element : list) {
            if (index >= 3) {
                break;
            }
            double numeric = numericValue(element, Double.NaN);
            if (Double.isNaN(numeric)) {
                return null;
            }
            values[index++] = numeric;
        }
        return index == 3 ? values : null;
    }

    private double readNumeric(CompoundBinaryTag compound, String key) {
        BinaryTag tag = compound.get(key);
        if (tag == null) {
            return Double.NaN;
        }
        return numericValue(tag, Double.NaN);
    }

    private double numericValue(BinaryTag tag, double fallback) {
        if (tag instanceof DoubleBinaryTag doubleTag) {
            return doubleTag.value();
        }
        if (tag instanceof FloatBinaryTag floatTag) {
            return floatTag.value();
        }
        if (tag instanceof IntBinaryTag intTag) {
            return intTag.value();
        }
        if (tag instanceof LongBinaryTag longTag) {
            return longTag.value();
        }
        if (tag instanceof ShortBinaryTag shortTag) {
            return shortTag.value();
        }
        if (tag instanceof ByteBinaryTag byteTag) {
            return byteTag.value();
        }
        return fallback;
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
        public void onMarkerMoved(Vector3 position, float yaw, float pitch, BinaryTag metadata) {
            properties.put("x", position.x);
            properties.put("y", position.y);
            properties.put("z", position.z);
            Map<String, Object> updates = new HashMap<>();
            updates.put("x", position.x);
            updates.put("y", position.y);
            updates.put("z", position.z);

            mergeLightMetadata(metadata, properties, updates);

            configureLightMarkerTags(this);
            ServerLightingManager.getInstance().applyTransformFromAxiom(id, updates);
        }

        @Override
        public void writeCustomMetadata(CompoundBinaryTag.Builder builder) {
            builder.putString("lightId", Long.toString(id));
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Number number) {
                    builder.putDouble(key, number.doubleValue());
                } else if (value instanceof Boolean bool) {
                    builder.putByte(key, (byte) (bool ? 1 : 0));
                } else if (value != null) {
                    builder.putString(key, value.toString());
                }
            }
        }

        @Override
        public String markerType() {
            return "light";
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
