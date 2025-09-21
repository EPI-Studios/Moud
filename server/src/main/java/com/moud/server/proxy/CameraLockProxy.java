package com.moud.server.proxy;

import com.moud.api.math.Vector3;
import com.moud.network.MoudPackets;
import com.moud.server.cursor.CursorService;
import com.moud.server.network.ServerNetworkManager;
import com.moud.server.player.PlayerCursorDirectionManager;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CameraLockProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraLockProxy.class);
    private final Player player;
    private boolean isLocked = false;
    private Vector3 position = Vector3.zero();
    private Vector3 rotation = Vector3.zero();
    private boolean smoothTransitions = false;
    private float transitionSpeed = 1.0f;
    private boolean disableViewBobbing = true;
    private boolean disableHandMovement = true;

    private Pos originalPosition;
    private boolean originalNoGravity;

    private static final ScheduledExecutorService animationExecutor = Executors.newScheduledThreadPool(2);
    private static final ConcurrentHashMap<Player, ScheduledFuture<?>> activeAnimations = new ConcurrentHashMap<>();

    public CameraLockProxy(Player player) {
        this.player = player;
    }

    @HostAccess.Export
    public void shake(float intensity, int durationMs) {
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraOffsetPacket(intensity, durationMs));
    }

    @HostAccess.Export
    public void lock(Vector3 position, Value options) {
        this.position = position;
        this.isLocked = true;

        if (options != null && options.hasMembers()) {
            if (options.hasMember("yaw")) {
                this.rotation = new Vector3(options.getMember("yaw").asFloat(), this.rotation.y, this.rotation.z);
            }
            if (options.hasMember("pitch")) {
                this.rotation = new Vector3(this.rotation.x, options.getMember("pitch").asFloat(), this.rotation.z);
            }
            if (options.hasMember("roll")) {
                this.rotation = new Vector3(this.rotation.x, this.rotation.y, options.getMember("roll").asFloat());
            }
            if (options.hasMember("smooth")) this.smoothTransitions = options.getMember("smooth").asBoolean();
            if (options.hasMember("speed")) this.transitionSpeed = options.getMember("speed").asFloat();
            if (options.hasMember("disableViewBobbing")) this.disableViewBobbing = options.getMember("disableViewBobbing").asBoolean();
            if (options.hasMember("disableHandMovement")) this.disableHandMovement = options.getMember("disableHandMovement").asBoolean();
        }

        this.originalPosition = player.getPosition();
        this.originalNoGravity = player.hasNoGravity();

        player.setInvisible(true);
        player.setNoGravity(true);
        player.teleport(new Pos(this.position.x, this.position.y, this.position.z, (float) this.rotation.x, (float) this.rotation.y));

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
                this.rotation = new Vector3(rotationValue.getMember("yaw").asFloat(), this.rotation.y, this.rotation.z);
            }
            if (rotationValue.hasMember("pitch")) {
                this.rotation = new Vector3(this.rotation.x, rotationValue.getMember("pitch").asFloat(), this.rotation.z);
            }
            if (rotationValue.hasMember("roll")) {
                this.rotation = new Vector3(this.rotation.x, this.rotation.y, rotationValue.getMember("roll").asFloat());
            }

            if (isLocked) {
                player.teleport(new Pos(player.getPosition(), (float)this.rotation.x, (float)this.rotation.y));
                CursorService.getInstance().updateCameraState(player, this.position, this.rotation, true);
                ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraUpdatePacket(this.position, this.rotation));
            }
        }
    }

    @HostAccess.Export
    public void release() {
        if (!isLocked) {
            ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraOffsetPacket(0, 0));
            return;
        }

        this.isLocked = false;
        stopAnimation();

        CursorService.getInstance().releaseCameraState(player);

        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraReleasePacket(false));
        ServerNetworkManager.getInstance().send(player, new MoudPackets.CameraOffsetPacket(0, 0));

        player.setInvisible(false);
        player.setNoGravity(originalNoGravity);

        if (this.originalPosition != null) {
            player.teleport(this.originalPosition);
        }
    }

    @HostAccess.Export
    public void smoothTransitionTo(Vector3 targetPosition, Value targetRotation, int durationMs) {
        if (!isLocked) return;

        stopAnimation();

        Pos startPos = player.getPosition();
        Vector3 startRot = this.rotation;

        Vector3 endRot;
        if (targetRotation != null && targetRotation.hasMembers()) {
            endRot = new Vector3(
                    targetRotation.hasMember("yaw") ? targetRotation.getMember("yaw").asFloat() : startRot.x,
                    targetRotation.hasMember("pitch") ? targetRotation.getMember("pitch").asFloat() : startRot.y,
                    targetRotation.hasMember("roll") ? targetRotation.getMember("roll").asFloat() : startRot.z
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
                player.teleport(new Pos(this.position.x, this.position.y, this.position.z, (float)this.rotation.x, (float)this.rotation.y));
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

            player.teleport(new Pos(this.position.x, this.position.y, this.position.z, (float)this.rotation.x, (float)this.rotation.y));
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