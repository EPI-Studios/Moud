package com.moud.server.cursor;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.player.PlayerCameraManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
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
        final double step = 0.05;
        Vec normalizedDirection = direction.normalize();
        if (normalizedDirection.lengthSquared() == 0) {
            return new RaycastResult(
                    new Vector3(origin.x(), origin.y(), origin.z()),
                    new Vector3(0, 1, 0),
                    false,
                    null
            );
        }

        Entity closestEntity = null;
        double closestEntityDistance = Double.MAX_VALUE;
        Point closestEntityHit = null;

        for (Entity entity : instance.getEntities()) {
            if (entity.equals(excludePlayer)) continue;

            double entityDistance = raycastToEntity(origin, normalizedDirection, entity, maxDistance);
            if (entityDistance >= 0 && entityDistance < closestEntityDistance) {
                closestEntityDistance = entityDistance;
                closestEntity = entity;
                closestEntityHit = origin.add(normalizedDirection.mul(entityDistance));
            }
        }

        Point lastPos = origin;
        for (double d = 0; d < maxDistance; d += step) {
            if (closestEntity != null && d >= closestEntityDistance) {
                return new RaycastResult(
                        new Vector3(closestEntityHit.x(), closestEntityHit.y(), closestEntityHit.z()),
                        new Vector3(0, 1, 0),
                        false,
                        closestEntity
                );
            }

            Point currentPos = origin.add(normalizedDirection.mul(d));
            Block block = instance.getBlock(currentPos);

            if (!block.isAir()) {
                Vector3 normal = calculateBlockNormal(lastPos, currentPos);
                Point hitPos = origin.add(normalizedDirection.mul(Math.max(0, d - step)));

                return new RaycastResult(
                        new Vector3(hitPos.x(), hitPos.y(), hitPos.z()),
                        normal,
                        true,
                        null
                );
            }
            lastPos = currentPos;
        }

        if (closestEntity != null) {
            return new RaycastResult(
                    new Vector3(closestEntityHit.x(), closestEntityHit.y(), closestEntityHit.z()),
                    new Vector3(0, 1, 0),
                    false,
                    closestEntity
            );
        }

        Point endPos = origin.add(normalizedDirection.mul(maxDistance));
        return new RaycastResult(
                new Vector3(endPos.x(), endPos.y(), endPos.z()),
                new Vector3(0, 1, 0),
                false,
                null
        );
    }

    private double raycastToEntity(Point origin, Vec direction, Entity entity, double maxDistance) {
        Pos entityPos = entity.getPosition();
        double entityRadius = 0.5;

        Vec toEntity = new Vec(
                entityPos.x() - origin.x(),
                entityPos.y() + entity.getBoundingBox().height() / 2 - origin.y(),
                entityPos.z() - origin.z()
        );

        double projectionLength = toEntity.dot(direction);
        if (projectionLength < 0 || projectionLength > maxDistance) {
            return -1;
        }

        Vec projection = direction.mul(projectionLength);
        Vec rejection = toEntity.sub(projection);

        if (rejection.length() <= entityRadius) {
            return Math.max(0, projectionLength - entityRadius);
        }

        return -1;
    }

    private Vector3 calculateBlockNormal(Point lastPos, Point currentPos) {
        int lastBlockX = lastPos.blockX();
        int lastBlockY = lastPos.blockY();
        int lastBlockZ = lastPos.blockZ();

        int currentBlockX = currentPos.blockX();
        int currentBlockY = currentPos.blockY();
        int currentBlockZ = currentPos.blockZ();

        if (currentBlockX > lastBlockX) return new Vector3(-1, 0, 0);
        if (currentBlockX < lastBlockX) return new Vector3(1, 0, 0);
        if (currentBlockY > lastBlockY) return new Vector3(0, -1, 0);
        if (currentBlockY < lastBlockY) return new Vector3(0, 1, 0);
        if (currentBlockZ > lastBlockZ) return new Vector3(0, 0, -1);
        if (currentBlockZ < lastBlockZ) return new Vector3(0, 0, 1);

        return new Vector3(0, 1, 0);
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
