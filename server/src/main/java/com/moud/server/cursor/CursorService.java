package com.moud.server.cursor;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.raycast.RaycastUtil;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.player.PlayerCameraManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CursorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CursorService.class);
    private static final double MAX_RAYCAST_DISTANCE = 100.0;
    private static final long UPDATE_INTERVAL_TICKS = 1L;
    private static final double MOVEMENT_THRESHOLD = 0.001;

    private static CursorService instance;
    private final Map<UUID, Cursor> cursors = new ConcurrentHashMap<>();
    private final Map<UUID, CameraState> cameraStates = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> hoveredEntities = new ConcurrentHashMap<>();
    private final ServerNetworkManager networkManager;
    private Task updateTask;

    public static synchronized void install(CursorService cursorService) {
        instance = Objects.requireNonNull(cursorService, "cursorService");
    }

    public CursorService(ServerNetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    public static synchronized CursorService getInstance(ServerNetworkManager networkManager) {
        if (instance == null) {
            instance = new CursorService(networkManager);
        }
        return instance;
    }

    public static synchronized CursorService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CursorService has not been initialized with a NetworkManager.");
        }
        return instance;
    }

    public void initialize() {
        this.updateTask = MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(TaskSchedule.tick((int) UPDATE_INTERVAL_TICKS))
                .schedule();
        LOGGER.info("Cursor service initialized.");
    }

    public void shutdown() {
        if (this.updateTask != null) {
            this.updateTask.cancel();
        }
        cursors.clear();
        cameraStates.clear();
        hoveredEntities.clear();
        LOGGER.info("Cursor service shut down.");
    }

    private void tick() {
        if (cursors.isEmpty()) return;

        List<MoudPackets.CursorUpdateData> positionUpdates = new ArrayList<>();
        for (Cursor cursor : cursors.values()) {
            if (cursor.isGloballyVisible()) {
                Vector3 oldPosition = cursor.getWorldPosition();
                updateCursorState(cursor);
                Vector3 newPosition = cursor.getWorldPosition();

                if (hasSignificantMovement(oldPosition, newPosition)) {
                    positionUpdates.add(new MoudPackets.CursorUpdateData(
                            cursor.getOwner().getUuid(),
                            newPosition,
                            cursor.getWorldNormal(),
                            cursor.isHittingBlock()
                    ));
                }
            }
        }

        if (!positionUpdates.isEmpty()) {
            networkManager.broadcast(new MoudPackets.CursorPositionUpdatePacket(positionUpdates));
        }
    }

    private boolean hasSignificantMovement(Vector3 oldPos, Vector3 newPos) {
        if (oldPos.equals(Vector3.zero()) && !newPos.equals(Vector3.zero())) return true;
        double distance = Math.sqrt(
                Math.pow(newPos.x - oldPos.x, 2) +
                        Math.pow(newPos.y - oldPos.y, 2) +
                        Math.pow(newPos.z - oldPos.z, 2)
        );
        return distance > MOVEMENT_THRESHOLD;
    }

    private void updateCursorState(Cursor cursor) {
        Player owner = cursor.getOwner();
        if (owner == null || !owner.isOnline() || owner.getInstance() == null) return;

        Point rayOrigin;
        Vector3 rayDirection;

        CameraState cameraState = cameraStates.get(owner.getUuid());
        if (cameraState != null && cameraState.isLocked) {
            rayOrigin = new Pos(cameraState.position.x, cameraState.position.y, cameraState.position.z);
            rayDirection = calculateDirectionFromRotation(cameraState.rotation);
        } else {
            rayOrigin = owner.getPosition().add(0, owner.getEyeHeight(), 0);
            Vector3 camDirVec = PlayerCameraManager.getInstance().getCameraDirection(owner);
            rayDirection = camDirVec;
        }

        Vec camDir = new Vec(rayDirection.x, rayDirection.y, rayDirection.z);
        Instance instance = owner.getInstance();

        RaycastResult result = performEntityAndBlockRaycast(instance, rayOrigin, camDir, MAX_RAYCAST_DISTANCE, owner);

        cursor.setTargetPosition(result.hitPosition(), result.blockNormal());
        cursor.setHittingBlock(result.isBlockHit());

        Entity oldHovered = hoveredEntities.get(owner.getUuid());
        Entity newHovered = result.hitEntity();

        if (oldHovered != newHovered) {
            if (oldHovered != null) {
                triggerEntityHoverExit(owner, oldHovered);
            }
            if (newHovered != null) {
                triggerEntityHoverEnter(owner, newHovered);
                hoveredEntities.put(owner.getUuid(), newHovered);
            } else {
                hoveredEntities.remove(owner.getUuid());
            }
        }
    }

    private Vector3 calculateDirectionFromRotation(Vector3 rotation) {
        double pitchRad = Math.toRadians(rotation.y);
        double yawRad = Math.toRadians(rotation.x);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);

        return new Vector3(x, y, z).normalize();
    }

    private void triggerEntityHoverEnter(Player player, Entity entity) {
        LOGGER.debug("Player {} cursor entered entity {}", player.getUsername(), entity.getUuid());
        dispatchEntityEvent(player, entity, "hover_enter");
    }

    private void triggerEntityHoverExit(Player player, Entity entity) {
        LOGGER.debug("Player {} cursor exited entity {}", player.getUsername(), entity.getUuid());
        dispatchEntityEvent(player, entity, "hover_exit");
    }

    private void dispatchEntityEvent(Player player, Entity entity, String interactionType) {
        try {
            com.moud.server.events.EventDispatcher eventDispatcher = com.moud.server.MoudEngine.getInstance().getEventDispatcher();
            if (eventDispatcher != null) {
                eventDispatcher.dispatchEntityInteraction(player, entity, interactionType);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not dispatch entity event: {}", e.getMessage());
        }
    }

    public void updateCameraState(Player player, Vector3 position, Vector3 rotation, boolean isLocked) {
        CameraState state = cameraStates.computeIfAbsent(player.getUuid(), k -> new CameraState());
        state.position = position;
        state.rotation = rotation;
        state.isLocked = isLocked;
    }

    public void releaseCameraState(Player player) {
        CameraState state = cameraStates.get(player.getUuid());
        if (state != null) {
            state.isLocked = false;
        }
    }

    private RaycastResult performEntityAndBlockRaycast(@NotNull Instance instance, @NotNull Point origin, @NotNull Vec direction, double maxDistance, Player excludePlayer) {
        var result = RaycastUtil.performRaycast(
                instance,
                origin,
                direction,
                maxDistance,
                entity -> excludePlayer == null || !entity.getUuid().equals(excludePlayer.getUuid())
        );
        boolean blockHit = result.block() != null;
        return new RaycastResult(
                result.position(),
                result.normal() != null ? result.normal() : new Vector3(0, 1, 0),
                blockHit,
                result.entity()
        );
    }

    private record RaycastResult(Vector3 hitPosition, Vector3 blockNormal, boolean isBlockHit, Entity hitEntity) {}

    private static class CameraState {
        Vector3 position = Vector3.zero();
        Vector3 rotation = Vector3.zero();
        boolean isLocked = false;
    }

    public void onPlayerJoin(Player player) {
        Cursor newCursor = new Cursor(player);
        newCursor.setGloballyVisible(false);
        cursors.put(player.getUuid(), newCursor);
        LOGGER.debug("Created hidden cursor for player {}", player.getUsername());

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (player.isOnline()) {
                syncAllCursorsToPlayer(player);
            }
        }).delay(TaskSchedule.tick(10)).schedule();
    }

    private void syncAllCursorsToPlayer(Player viewer) {
        LOGGER.debug("Syncing all visible cursors to new player {}", viewer.getUsername());
        for (Cursor cursorToSync : cursors.values()) {
            if (cursorToSync.getOwner().equals(viewer)) continue;

            syncCursorToViewer(cursorToSync, viewer);
        }
    }

    public void onPlayerQuit(Player player) {
        cursors.remove(player.getUuid());
        cameraStates.remove(player.getUuid());
        hoveredEntities.remove(player.getUuid());
        networkManager.broadcast(new MoudPackets.RemoveCursorsPacket(List.of(player.getUuid())));
    }

    public Cursor getCursor(Player player) {
        return cursors.get(player.getUuid());
    }

    public void sendAppearanceUpdate(Cursor cursor) {
        MoudPackets.CursorAppearancePacket packet = new MoudPackets.CursorAppearancePacket(
                cursor.getOwner().getUuid(),
                cursor.getTexture(),
                cursor.getColor(),
                cursor.getScale(),
                cursor.getRenderMode().name()
        );
        networkManager.broadcast(packet);
    }

    public void syncCursorToViewer(Cursor cursor, Player viewer) {
        if (cursor == null || viewer == null || !viewer.isOnline()) return;

        boolean isVisible = cursor.isVisibleTo(viewer);

        if (isVisible) {
            networkManager.send(viewer, new MoudPackets.CursorVisibilityPacket(cursor.getOwner().getUuid(), true));

            MoudPackets.CursorAppearancePacket appearancePacket = new MoudPackets.CursorAppearancePacket(
                    cursor.getOwner().getUuid(),
                    cursor.getTexture(),
                    cursor.getColor(),
                    cursor.getScale(),
                    cursor.getRenderMode().name()
            );
            networkManager.send(viewer, appearancePacket);
        } else {
            networkManager.send(viewer, new MoudPackets.CursorVisibilityPacket(cursor.getOwner().getUuid(), false));
        }
    }

    public void sendVisibilityUpdate(Cursor cursor) {
        for (Player viewer : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (viewer.getUuid().equals(cursor.getOwner().getUuid())) continue;

            boolean canSee = cursor.isVisibleTo(viewer);
            networkManager.send(viewer, new MoudPackets.CursorVisibilityPacket(cursor.getOwner().getUuid(), canSee));
        }
    }

    public Entity getHoveredEntity(Player player) {
        return hoveredEntities.get(player.getUuid());
    }
}
