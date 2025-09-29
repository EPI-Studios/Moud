package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.cursor.CursorService;
import com.moud.server.player.PlayerCursorDirectionManager;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CameraLockProxy {
    private final Player player;
    private Vector3 position;
    private Vector3 rotation;
    private boolean isLocked = false;
    private boolean smoothTransitions = false;
    private float transitionSpeed = 1.0f;
    private boolean disableViewBobbing = true;
    private boolean disableHandMovement = true;

    private static final ScheduledExecutorService animationExecutor = Executors.newScheduledThreadPool(2);
    private static final ConcurrentHashMap<Player, ScheduledFuture<?>> activeAnimations = new ConcurrentHashMap<>();

    public CameraLockProxy(Player player) {
        this.player = player;
        this.position = new Vector3(0, 0, 0);
        this.rotation = new Vector3(0, 0, 0);
    }

    private float safeValueToFloat(Value value) {
        if (value.isNumber()) {
            if (value.fitsInFloat()) {
                return value.asFloat();
            } else if (value.fitsInDouble()) {
                return (float) value.asDouble();
            } else if (value.fitsInLong()) {
                return (float) value.asLong();
            } else if (value.fitsInInt()) {
                return (float) value.asInt();
            }
        }
        return 0.0f;
    }

    @HostAccess.Export
    public void lock(Vector3 position, Value options) {
        this.position = position;
        this.isLocked = true;

        float yaw = 0.0f;
        float pitch = 0.0f;
        float roll = 0.0f;

        if (options != null && options.hasMembers()) {
            if (options.hasMember("yaw")) {
                yaw = safeValueToFloat(options.getMember("yaw"));
            }
            if (options.hasMember("pitch")) {
                pitch = safeValueToFloat(options.getMember("pitch"));
            }
            if (options.hasMember("roll")) {
                roll = safeValueToFloat(options.getMember("roll"));
            }
            if (options.hasMember("smooth")) {
                this.smoothTransitions = options.getMember("smooth").asBoolean();
            }
            if (options.hasMember("speed")) {
                this.transitionSpeed = safeValueToFloat(options.getMember("speed"));
            }
            if (options.hasMember("disableViewBobbing")) {
                this.disableViewBobbing = options.getMember("disableViewBobbing").asBoolean();
            }
            if (options.hasMember("disableHandMovement")) {
                this.disableHandMovement = options.getMember("disableHandMovement").asBoolean();
            }
        }

        this.rotation = new Vector3(yaw, pitch, roll);

        player.teleport(new Pos(this.position.x, this.position.y, this.position.z, yaw, pitch));

        CursorService.getInstance().updateCameraState(player, this.position, this.rotation, true);

        ServerNetworkManager.getInstance().send(player, new MoudPackets.AdvancedCameraLockPacket(
                this.position, this.rotation, this.smoothTransitions, this.transitionSpeed,
                this.disableViewBobbing, this.disableHandMovement, true));
    }

    @HostAccess.Export
    public void setPosition(Vector3 newPosition) {
        this.position = newPosition;
        if (isLocked) {
            player.teleport(new Pos(this.position.x, this.position.y, this.position.z, player.getPosition().yaw(), player.getPosition().pitch()));
            CursorService.getInstance().updateCameraState(player, this.position, this.rotation, true);
            ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraUpdatePacket(this.position, this.rotation));
        }
    }

    @HostAccess.Export
    public void setRotation(Value rotationValue) {
        if (rotationValue != null && rotationValue.hasMembers()) {
            if (rotationValue.hasMember("yaw")) {
                this.rotation = new Vector3(safeValueToFloat(rotationValue.getMember("yaw")), this.rotation.y, this.rotation.z);
            }
            if (rotationValue.hasMember("pitch")) {
                this.rotation = new Vector3(this.rotation.x, safeValueToFloat(rotationValue.getMember("pitch")), this.rotation.z);
            }
            if (rotationValue.hasMember("roll")) {
                this.rotation = new Vector3(this.rotation.x, this.rotation.y, safeValueToFloat(rotationValue.getMember("roll")));
            }

            if (isLocked) {
                player.teleport(new Pos(this.position.x, this.position.y, this.position.z, this.rotation.x, this.rotation.y));
                CursorService.getInstance().updateCameraState(player, this.position, this.rotation, true);
                ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraUpdatePacket(this.position, this.rotation));
            }
        }
    }

    @HostAccess.Export
    public void release() {
        this.isLocked = false;
        stopAnimation();
        CursorService.getInstance().updateCameraState(player, null, null, false);
        ServerNetworkManager.getInstance().send(player, new MoudPackets.AdvancedCameraLockPacket(
                null, null, false, 1.0f, true, true, false));
    }

    @HostAccess.Export
    public void smoothTransitionTo(Vector3 targetPosition, Value targetRotation, long durationMs) {
        if (!isLocked) return;

        stopAnimation();

        Pos startPos = player.getPosition();
        Vector3 startRot = this.rotation;
        Vector3 endRot;

        if (targetRotation != null && targetRotation.hasMembers()) {
            endRot = new Vector3(
                    targetRotation.hasMember("yaw") ? safeValueToFloat(targetRotation.getMember("yaw")) : startRot.x,
                    targetRotation.hasMember("pitch") ? safeValueToFloat(targetRotation.getMember("pitch")) : startRot.y,
                    targetRotation.hasMember("roll") ? safeValueToFloat(targetRotation.getMember("roll")) : startRot.z
            );
        } else {
            endRot = startRot;
        }

        long startTime = System.currentTimeMillis();

        ScheduledFuture<?> transitionTask = animationExecutor.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= durationMs) {
                this.position = targetPosition;
                this.rotation = endRot;
                player.teleport(new Pos(this.position.x, this.position.y, this.position.z, this.rotation.x, this.rotation.y));
                CursorService.getInstance().updateCameraState(player, this.position, this.rotation, true);
                ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraUpdatePacket(this.position, this.rotation));
                stopAnimation();
                return;
            }

            float progress = (float) elapsed / durationMs;
            progress = easeInOutCubic(progress);

            Vector3 startPosVec = new Vector3(startPos.x(), startPos.y(), startPos.z());
            this.position = lerpVector3(startPosVec, targetPosition, progress);
            this.rotation = lerpVector3(startRot, endRot, progress);

            player.teleport(new Pos(this.position.x, this.position.y, this.position.z, this.rotation.x, this.rotation.y));
            CursorService.getInstance().updateCameraState(player, this.position, this.rotation, true);
            ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraUpdatePacket(this.position, this.rotation));

        }, 0, 16, TimeUnit.MILLISECONDS);

        activeAnimations.put(player, transitionTask);
    }

    @HostAccess.Export
    public void resetCursorRotation() {
        PlayerCursorDirectionManager.getInstance().resetRotation(player);
    }

    @HostAccess.Export
    public void stopAnimation() {
        ScheduledFuture<?> currentAnimation = activeAnimations.remove(player);
        if (currentAnimation != null) {
            currentAnimation.cancel(false);
        }
    }

    @HostAccess.Export
    public boolean isLocked() {
        return isLocked;
    }

    @HostAccess.Export
    public Vector3 getPosition() {
        return position;
    }

    @HostAccess.Export
    public Vector3 getRotation() {
        return rotation;
    }

    private Vector3 lerpVector3(Vector3 start, Vector3 end, float t) {
        return new Vector3(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t, start.z + (end.z - start.z) * t);
    }

    private float easeInOutCubic(float t) {
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }
}