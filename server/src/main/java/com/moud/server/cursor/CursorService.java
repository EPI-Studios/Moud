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
    private static final long UPDATE_INTERVAL_TICKS = 2L;

    private static CursorService instance;
    private final Map<UUID, Cursor> cursors = new ConcurrentHashMap<>();
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

        Vector3 camDirVec = PlayerCameraManager.getInstance().getCameraDirection(owner);
        Vec camDir = new Vec(camDirVec.x, camDirVec.y, camDirVec.z);
        Pos eyePos = owner.getPosition().add(0, owner.getEyeHeight(), 0);
        Instance instance = owner.getInstance();

        ManualRaycastResult result = performRaycast(instance, eyePos, camDir, MAX_RAYCAST_DISTANCE);

        cursor.setWorldPosition(result.hitPosition());
        cursor.setWorldNormal(result.blockNormal());
        cursor.setHittingBlock(result.isHit());
    }

    private ManualRaycastResult performRaycast(@NotNull Instance instance, @NotNull Point origin, @NotNull Vec direction, double maxDistance) {
        final double step = 0.1;
        Vec normalizedDirection = direction.normalize();
        Point lastPos = origin;

        for (double d = 0; d < maxDistance; d += step) {
            Point currentPos = origin.add(normalizedDirection.mul(d));
            Block block = instance.getBlock(currentPos);

            if (!block.isAir()) {
                Vec normal = Vec.ZERO;

                int lastBlockX = lastPos.blockX();
                int lastBlockY = lastPos.blockY();
                int lastBlockZ = lastPos.blockZ();

                int currentBlockX = currentPos.blockX();
                int currentBlockY = currentPos.blockY();
                int currentBlockZ = currentPos.blockZ();

                if (currentBlockX > lastBlockX) normal = new Vec(-1, 0, 0);
                else if (currentBlockX < lastBlockX) normal = new Vec(1, 0, 0);
                else if (currentBlockY > lastBlockY) normal = new Vec(0, -1, 0);
                else if (currentBlockY < lastBlockY) normal = new Vec(0, 1, 0);
                else if (currentBlockZ > lastBlockZ) normal = new Vec(0, 0, -1);
                else if (currentBlockZ < lastBlockZ) normal = new Vec(0, 0, 1);
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

    public void onPlayerJoin(Player player) {
        cursors.put(player.getUuid(), new Cursor(player));

        for (Player otherPlayer : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (!otherPlayer.equals(player)) {
                syncCursorToViewer(getCursor(otherPlayer), player);
            }
        }
    }

    public void onPlayerQuit(Player player) {
        cursors.remove(player.getUuid());
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