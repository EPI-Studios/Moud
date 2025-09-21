package com.moud.server.cursor;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.player.PlayerCameraManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CursorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CursorService.class);
    private static final double MAX_RAYCAST_DISTANCE = 100.0;
    private static final long UPDATE_INTERVAL_TICKS = 1L;

    private static CursorService instance;
    private final Map<UUID, Cursor> cursors = new ConcurrentHashMap<>();
    private final Map<UUID, CameraState> cameraStates = new ConcurrentHashMap<>();
    private final ServerNetworkManager networkManager;
    private Task updateTask;

    private CursorService(ServerNetworkManager networkManager) {
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
        LOGGER.info("Cursor service shut down.");
    }

    private void tick() {
        if (cursors.isEmpty()) return;

        List<MoudPackets.CursorUpdateData> positionUpdates = new ArrayList<>();
        for (Cursor cursor : cursors.values()) {
            if (cursor.isGloballyVisible()) {
                updateCursorState(cursor);

                positionUpdates.add(new MoudPackets.CursorUpdateData(
                        cursor.getOwner().getUuid(),
                        cursor.getWorldPosition(),
                        cursor.getWorldNormal(),
                        cursor.isHittingBlock()
                ));
            }
        }

        if (!positionUpdates.isEmpty()) {
            networkManager.broadcast(new MoudPackets.CursorPositionUpdatePacket(positionUpdates));
        }
    }

    private void updateCursorState(Cursor cursor) {
        Player owner = cursor.getOwner();
        if (owner == null || !owner.isOnline() || owner.getInstance() == null) return;

        // --- THIS IS THE FIX ---
        // The raycast origin is *always* the player's eye position.
        // When the camera is locked, the player entity itself is moved to the camera's location,
        // so this calculation works for both locked and unlocked states.
        Point rayOrigin = owner.getPosition().add(0, owner.getEyeHeight(), 0);
        Vector3 camDirVec = PlayerCameraManager.getInstance().getCameraDirection(owner);

        Vec camDir = new Vec(camDirVec.x, camDirVec.y, camDirVec.z);
        Instance instance = owner.getInstance();
        ManualRaycastResult result = performRaycast(instance, rayOrigin, camDir, MAX_RAYCAST_DISTANCE);

        cursor.setTargetPosition(result.hitPosition(), result.blockNormal());
        cursor.setHittingBlock(result.isHit());
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

    private ManualRaycastResult performRaycast(@NotNull Instance instance, @NotNull Point origin, @NotNull Vec direction, double maxDistance) {
        final double step = 0.1;
        Vec normalizedDirection = direction.normalize();
        if (normalizedDirection.lengthSquared() == 0) {
            // Avoid division by zero or invalid direction
            return new ManualRaycastResult(
                    new Vector3(origin.x(), origin.y(), origin.z()),
                    new Vector3(0, 1, 0),
                    false
            );
        }
        Point lastPos = origin;

        for (double d = 0; d < maxDistance; d += step) {
            Point currentPos = origin.add(normalizedDirection.mul(d));
            Block block = instance.getBlock(currentPos);

            if (!block.isAir()) {
                int lastBlockX = lastPos.blockX();
                int lastBlockY = lastPos.blockY();
                int lastBlockZ = lastPos.blockZ();

                int currentBlockX = currentPos.blockX();
                int currentBlockY = currentPos.blockY();
                int currentBlockZ = currentPos.blockZ();

                Vec normal = Vec.ZERO;
                int axis = -1;
                double plane = 0;

                if (currentBlockX > lastBlockX) {
                    normal = new Vec(-1, 0, 0);
                    axis = 0;
                    plane = currentBlockX;
                } else if (currentBlockX < lastBlockX) {
                    normal = new Vec(1, 0, 0);
                    axis = 0;
                    plane = currentBlockX + 1;
                } else if (currentBlockY > lastBlockY) {
                    normal = new Vec(0, -1, 0);
                    axis = 1;
                    plane = currentBlockY;
                } else if (currentBlockY < lastBlockY) {
                    normal = new Vec(0, 1, 0);
                    axis = 1;
                    plane = currentBlockY + 1;
                } else if (currentBlockZ > lastBlockZ) {
                    normal = new Vec(0, 0, -1);
                    axis = 2;
                    plane = currentBlockZ;
                } else if (currentBlockZ < lastBlockZ) {
                    normal = new Vec(0, 0, 1);
                    axis = 2;
                    plane = currentBlockZ + 1;
                }

                if (axis != -1) {
                    double originComp = switch (axis) {
                        case 0 -> origin.x();
                        case 1 -> origin.y();
                        case 2 -> origin.z();
                        default -> throw new IllegalStateException("Invalid axis");
                    };
                    double dirComp = switch (axis) {
                        case 0 -> normalizedDirection.x();
                        case 1 -> normalizedDirection.y();
                        case 2 -> normalizedDirection.z();
                        default -> throw new IllegalStateException("Invalid axis");
                    };
                    if (Math.abs(dirComp) < 1e-6) {
                        // Parallel to plane, skip (shouldn't happen as coord wouldn't change)
                        continue;
                    }
                    double t = (plane - originComp) / dirComp;
                    if (t < 0) {
                        // Behind origin, invalid
                        continue;
                    }
                    Point hitPos = origin.add(normalizedDirection.mul(t));
                    return new ManualRaycastResult(
                            new Vector3(hitPos.x(), hitPos.y(), hitPos.z()),
                            new Vector3(normal.x(), normal.y(), normal.z()),
                            true
                    );
                }
                // Fallback if no axis change (e.g., started inside), but shouldn't reach here
                return new ManualRaycastResult(
                        new Vector3(currentPos.x(), currentPos.y(), currentPos.z()),
                        new Vector3(normal.x(), normal.y(), normal.z()),
                        true
                );
            }
            lastPos = currentPos;
        }

        Point endPos = origin.add(normalizedDirection.mul(maxDistance));
        return new ManualRaycastResult(
                new Vector3(endPos.x(), endPos.y(), endPos.z()),
                new Vector3(0, 1, 0),
                false
        );
    }
    private record ManualRaycastResult(Vector3 hitPosition, Vector3 blockNormal, boolean isHit) {}

    private static class CameraState {
        Vector3 position = Vector3.zero();
        Vector3 rotation = Vector3.zero();
        boolean isLocked = false;
    }

    public void onPlayerJoin(Player player) {
        Cursor newCursor = new Cursor(player);
        newCursor.setTexture("minecraft:textures/block/white_concrete.png");
        newCursor.setColor(new Vector3(1.0f, 1.0f, 1.0f));
        newCursor.setScale(0.8f);
        newCursor.setGloballyVisible(false);

        cursors.put(player.getUuid(), newCursor);

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            newCursor.setGloballyVisible(true);

            for (Player otherPlayer : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                if (!otherPlayer.equals(player)) {
                    syncCursorToViewer(getCursor(otherPlayer), player);
                }
            }

            syncCursorToViewer(newCursor, player);
        }).delay(TaskSchedule.tick(40)).schedule();
    }

    public void onPlayerQuit(Player player) {
        cursors.remove(player.getUuid());
        cameraStates.remove(player.getUuid());
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
        if (cursor == null) return;

        networkManager.send(viewer, new MoudPackets.CursorVisibilityPacket(cursor.getOwner().getUuid(), cursor.isVisibleTo(viewer)));

        MoudPackets.CursorAppearancePacket appearancePacket = new MoudPackets.CursorAppearancePacket(
                cursor.getOwner().getUuid(),
                cursor.getTexture(),
                cursor.getColor(),
                cursor.getScale(),
                cursor.getRenderMode().name()
        );
        networkManager.send(viewer, appearancePacket);
    }

    public void sendVisibilityUpdate(Cursor cursor) {
        for (Player viewer : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (viewer.getUuid().equals(cursor.getOwner().getUuid())) continue;

            boolean canSee = cursor.isVisibleTo(viewer);
            networkManager.send(viewer, new MoudPackets.CursorVisibilityPacket(cursor.getOwner().getUuid(), canSee));
        }
    }
}